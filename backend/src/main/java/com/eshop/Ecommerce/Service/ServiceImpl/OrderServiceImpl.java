package com.eshop.Ecommerce.Service.ServiceImpl;

import com.eshop.Ecommerce.Exception.APIException;
import com.eshop.Ecommerce.Exception.ResourceNotFoundException;
import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Payload.OrderDTO;
import com.eshop.Ecommerce.Payload.OrderItemDTO;
import com.eshop.Ecommerce.Payload.OrderResponse;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.CartService;
import com.eshop.Ecommerce.Service.OrderService;
import com.eshop.Ecommerce.Util.AuthUtil;
import com.eshop.Ecommerce.extension.DomainEvents;
import com.eshop.Ecommerce.Service.CartOfferService;
import com.eshop.Ecommerce.extension.OrderTrackingService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    AddressRepository addressRepository;

    @Autowired
    CartOfferService offerPricing;

    @Autowired
    OrderItemRepo orderItemRepository;

    @Autowired
    OrderRepo orderRepository;

    @Autowired
    PaymentRepo paymentRepository;

    @Autowired
    CartService cartService;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    ProductRepo productRepository;

    @Autowired
    AuthUtil authUtil;
    @Autowired
    DomainEvents domainEvents;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate emailDeliveryJdbc;
    @Autowired
    OrderTrackingService tracking;

    @Override
    @Transactional
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        // Public order route can never claim that an online payment succeeded.
        if (!"COD".equalsIgnoreCase(paymentMethod)) {
            throw new APIException("Use Stripe Checkout for online payments; only COD can be placed directly");
        }
        return saveOrder(emailId,addressId,false,null);
    }

    @Override
    @Transactional
    public OrderDTO placeVerifiedStripeOrder(String emailId, Long addressId, String stripeSessionId) {
        if(stripeSessionId==null||stripeSessionId.isBlank())throw new APIException("Missing verified Stripe session");
        return saveOrder(emailId,addressId,true,stripeSessionId);
    }

    private OrderDTO saveOrder(String emailId,Long addressId,boolean stripeVerified,String stripeSessionId){
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        if(address.getUser()==null || !emailId.equalsIgnoreCase(address.getUser().getEmail())) {
            throw new APIException("Shipping address does not belong to the current customer");
        }
        // Calculate server-authoritative subtotal and coupon from persisted cart items.
        CartOfferService.Quote quote=offerPricing.quote(cart,emailId,true);
        if(quote.total()<=0)throw new APIException("Cannot place a zero-value order");
        // Global/per-customer limits are checked before inserting this new order,
        // avoiding counting the current transaction's own order as a prior redemption.
        // Any subsequent stock failure rolls back the coupon counter as well.
        offerPricing.redeem(quote,emailId,cart);
        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setOrderSubtotal(quote.subtotal());
        order.setDiscountAmount(quote.discount());
        order.setOfferCode(quote.applied()?quote.code():null);
        order.setTotalAmount(quote.total());
        order.setOrderStatus("Accepted");
        order.setAddress(address);
        String snapshot=String.join(", ",address.getBuildingName(),address.getStreet(),
            address.getCity(),address.getState(),address.getPincode(),address.getCountry());
        order.setShippingAddressSnapshot(snapshot.length()>1000?snapshot.substring(0,1000):snapshot);

        Payment payment = stripeVerified
            ? new Payment("STRIPE", stripeSessionId, "PAID", "Verified Stripe Checkout webhook", "Stripe")
            : new Payment("CASH_ON_DELIVERY", null, "PENDING", "Pay on delivery", "COD");
        Order savedOrder = orderRepository.save(order);
        payment.setOrder(savedOrder);
        payment = paymentRepository.save(payment);
        savedOrder.setPayment(payment);
        savedOrder = orderRepository.save(savedOrder);

        List<CartItem> cartItems = new ArrayList<>(cart.getCartItems());
        if (cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }

        // Deterministic locking order prevents inter-product deadlocks.
        for (CartItem item : cartItems.stream().sorted(java.util.Comparator.comparing(i -> i.getProduct().getProductId())).toList()) {
            if (item.getQuantity() == null || item.getQuantity() < 1 ||
                productRepository.reserveStock(item.getProduct().getProductId(),item.getQuantity()) != 1) {
                throw new APIException("Insufficient stock for product " + item.getProduct().getProductId());
            }
        }
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            String name=cartItem.getProduct().getProductName();
            String variant=cartItem.getProduct().getVariantLabel();
            if(variant!=null&&!variant.isBlank())name=name+" / "+variant;
            orderItem.setOrderedProductName(name.length()>255?name.substring(0,255):name);
            orderItem.setOrder(savedOrder);
            orderItems.add(orderItem);
        }

        orderItems = orderItemRepository.saveAll(orderItems);

        new ArrayList<>(cart.getCartItems()).forEach(item -> {
            int quantity = item.getQuantity();
            Product product = item.getProduct();

            // Stock was already reserved atomically in PostgreSQL above.

            // Remove items from cart
            cartService.deleteProductFromCart(cart.getCartId(), item.getProduct().getProductId());
        });

        // The cart is now empty; coupon redemption happened before order persistence.
        cart.setCouponCode(null);
        cartRepository.save(cart);

        tracking.recordPlaced(savedOrder);
        // One durable email job per committed order, in the same PostgreSQL
        // transaction as stock, payment, order and event outbox. No SMTP call
        // occurs during the checkout HTTP request.
        emailDeliveryJdbc.update(
            "insert into order_confirmation_emails(order_id) values (?) on conflict do nothing",
            savedOrder.getOrderId());
        domainEvents.record("ORDER_PLACED", null, savedOrder.getOrderId());
        for (CartItem item : cartItems) {
            domainEvents.record("INVENTORY_CHANGED", item.getProduct().getProductId(), savedOrder.getOrderId());
        }
        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        if (orderDTO.getOrderItems() == null) orderDTO.setOrderItems(new ArrayList<>());
        orderItems.forEach(item -> orderDTO.getOrderItems().add(modelMapper.map(item, OrderItemDTO.class)));

        orderDTO.setAddressId(addressId);

        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Order> pageOrders = orderRepository.findAll(pageDetails);
        List<Order> orders = pageOrders.getContent();
        List<OrderDTO> orderDTOs = orders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public OrderDTO updateOrder(Long orderId, String status) {
        Order updated=tracking.updateAdmin(orderId,new OrderTrackingService.UpdateRequest(status,null,null,null,null));
        return modelMapper.map(updated, OrderDTO.class);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public OrderDTO updateSellerOrder(Long orderId, String status) {
        Order updated=tracking.updateSeller(orderId,new OrderTrackingService.UpdateRequest(status,null,null,null,null));
        return modelMapper.map(updated, OrderDTO.class);
    }

    // get all seller orders for seller panel :>
    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findAll(pageDetails);

        List<Order> sellerOrders = pageOrders.getContent().stream()
                .filter(order -> order.getOrderItems().stream()
                        .anyMatch(orderItem -> {
                            var product = orderItem.getProduct();
                            if (product == null || product.getUser() == null) {
                                return false;
                            }
                            return product.getUser().getUserid().equals(
                                    seller.getUserid());
                        }))
                .toList();

        List<OrderDTO> orderDTOs = sellerOrders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }


}