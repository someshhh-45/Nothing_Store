package com.eshop.Ecommerce.Service.ServiceImpl;

import com.eshop.Ecommerce.Exception.APIException;
import com.eshop.Ecommerce.Exception.ResourceNotFoundException;
import com.eshop.Ecommerce.Model.Cart;
import com.eshop.Ecommerce.Model.CartItem;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Payload.CartDTO;
import com.eshop.Ecommerce.Payload.CartItemDTO;
import com.eshop.Ecommerce.Payload.ProductDTO;
import com.eshop.Ecommerce.Repositories.CartItemRepository;
import com.eshop.Ecommerce.Repositories.CartRepository;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Service.CartService;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    CartRepository cartRepository;
    @Autowired
    public AuthUtil authUtil;
    @Autowired
    ProductRepo productRepo;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ModelMapper modelMapper;
    @Autowired
    com.eshop.Ecommerce.Service.RetailerPricing retailerPricing;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override
    public CartDTO addProductToCart(Long productId, Integer quantity) {
        Cart cart  = createCart();

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cart.getCartId(), productId);

        if (cartItem != null) {
            throw new APIException("Product " + product.getProductName() + " already exists in the cart");
        }

        if (product.getQuantity() == 0) {
            throw new APIException(product.getProductName() + " is not available");
        }

        if (product.getQuantity() < quantity) {
            throw new APIException("Please, make an order of the " + product.getProductName()
                    + " less than or equal to the quantity " + product.getQuantity() + ".");
        }

        CartItem newCartItem = new CartItem();

        newCartItem.setProduct(product);
        newCartItem.setCart(cart);
        newCartItem.setQuantity(quantity);
        newCartItem.setDiscount(product.getDiscount());
        newCartItem.setProductPrice(retailerPricing.customerPrice(authUtil.loggedInUser(),product.getSpecialPrice()));

        cartItemRepository.save(newCartItem);

        product.setQuantity(product.getQuantity());

        cart.setTotalPrice(cart.getTotalPrice() + (retailerPricing.customerPrice(authUtil.loggedInUser(),product.getSpecialPrice()) * quantity));

        cartRepository.save(cart);

        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();

        Stream<ProductDTO> productStream = cartItems.stream().map(item -> {
            ProductDTO map = modelMapper.map(item.getProduct(), ProductDTO.class);
            map.setQuantity(item.getQuantity());
            return map;
        });

        cartDTO.setProducts(productStream.toList());

        return cartDTO;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CartDTO> getCarts() {
        List<Cart> carts=cartRepository.findAll();
        if(carts.isEmpty()){
            throw new APIException("No carts found");
        }
        List<CartDTO> cartDTOs = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

            List<ProductDTO> products = cart.getCartItems().stream().map(cartItem -> {
                ProductDTO productDTO = modelMapper.map(cartItem.getProduct(), ProductDTO.class);
                productDTO.setQuantity(cartItem.getQuantity()); // Set the quantity from CartItem
                return productDTO;
            }).collect(Collectors.toList());


            cartDTO.setProducts(products);

            return cartDTO;

        }).collect(Collectors.toList());

        return cartDTOs;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public CartDTO getCartById(String emailId, Long cartId) {
        Cart cart= cartRepository.findCartByEmailAndCartId(emailId,cartId);
        if(cart==null){
            throw new ResourceNotFoundException("Cart", "cartId", cartId);
        }
        CartDTO cartDTO= modelMapper.map(cart, CartDTO.class);
        List<ProductDTO> products= cart.getCartItems().stream()
                .map(item -> {
                    ProductDTO result=modelMapper.map(item.getProduct(),ProductDTO.class);
                    result.setQuantity(item.getQuantity());
                    return result;
                }).toList();
        cartDTO.setProducts(products);

        return cartDTO;
    }

    @Transactional
    @Override
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity) {

        String emailId = authUtil.loggedInEmail();
        Cart userCart = cartRepository.findCartByEmail(emailId);
        Long cartId  = userCart.getCartId();

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getQuantity() == 0) {
            throw new APIException(product.getProductName() + " is not available");
        }

        if (product.getQuantity() < quantity) {
            throw new APIException("Please, make an order of the " + product.getProductName()
                    + " less than or equal to the quantity " + product.getQuantity() + ".");
        }

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        // Calculate new quantity
        int newQuantity = cartItem.getQuantity() + quantity;

        // Validation to prevent negative quantities
        if (newQuantity < 0) {
            throw new APIException("The resulting quantity cannot be negative.");
        }

        if (newQuantity == 0){
            deleteProductFromCart(cartId, productId);
        } else {
            cartItem.setProductPrice(retailerPricing.customerPrice(authUtil.loggedInUser(),product.getSpecialPrice()));
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
            cartItem.setDiscount(product.getDiscount());
            cart.setTotalPrice(cart.getTotalPrice() + (cartItem.getProductPrice() * quantity));
            cartRepository.save(cart);
        }

        CartItem updatedItem = cartItemRepository.save(cartItem);
        if(updatedItem.getQuantity() == 0){
            cartItemRepository.deleteById(updatedItem.getCartItemId());
        }


        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();

        Stream<ProductDTO> productStream = cartItems.stream().map(item -> {
            ProductDTO prd = modelMapper.map(item.getProduct(), ProductDTO.class);
            prd.setQuantity(item.getQuantity());
            return prd;
        });


        cartDTO.setProducts(productStream.toList());

        return cartDTO;
    }


    @Transactional
    @Override
    public String deleteProductFromCart(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }

        cart.setTotalPrice(cart.getTotalPrice() -
                (cartItem.getProductPrice() * cartItem.getQuantity()));

        cartItemRepository.deleteCartItemByProductIdAndCartId(cartId, productId);

        return "Product " + cartItem.getProduct().getProductName() + " removed from the cart !!!";
    }

    @Override
    public void updateProductInCarts(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        double cartPrice = cart.getTotalPrice()
                - (cartItem.getProductPrice() * cartItem.getQuantity());

        cartItem.setProductPrice(retailerPricing.customerPrice(authUtil.loggedInUser(),product.getSpecialPrice()));

        cart.setTotalPrice(cartPrice
                + (cartItem.getProductPrice() * cartItem.getQuantity()));

        cartItem = cartItemRepository.save(cartItem);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String createOrUpdateCartWithItems(List<CartItemDTO> items) {
        if(items==null||items.size()>100)throw new APIException("Invalid cart size");
        String emailId=authUtil.loggedInEmail();
        Cart cart=cartRepository.findCartByEmail(emailId);
        if(cart==null){
            cart=new Cart();
            cart.setUser(authUtil.loggedInUser());
            cart.setTotalPrice(0.0);
            cart=cartRepository.save(cart);
        }
        java.util.Set<Long> seen=new java.util.HashSet<>();
        for(CartItemDTO item:items){
            if(item==null||item.getProductId()==null||!seen.add(item.getProductId())||
               item.getQuantity()==null||item.getQuantity()<1||item.getQuantity()>99){
                throw new APIException("Invalid cart item or duplicate product");
            }
        }
        // Bulk-delete old rows, flush and clear the first-level cache, then reload the cart.
        // Otherwise a stale Hibernate collection can reinsert duplicate cart items.
        Long cartId=cart.getCartId();
        cartItemRepository.deleteAllByCartId(cartId);
        cartItemRepository.flush();
        entityManager.clear();
        cart=cartRepository.findById(cartId)
          .orElseThrow(()->new ResourceNotFoundException("Cart","cartId",cartId));
        double total=0;
        for(CartItemDTO item:items){
            Product product=productRepo.findById(item.getProductId())
              .orElseThrow(()->new ResourceNotFoundException("Product","productId",item.getProductId()));
            if(product.getQuantity()==null||product.getQuantity()<item.getQuantity())
              throw new APIException("Insufficient stock for "+product.getProductName());
            CartItem ci=new CartItem();
            ci.setCart(cart);ci.setProduct(product);
            ci.setQuantity(item.getQuantity());ci.setDiscount(product.getDiscount());
            ci.setProductPrice(retailerPricing.customerPrice(authUtil.loggedInUser(),product.getSpecialPrice()));
            cart.getCartItems().add(ci);
            total+=ci.getProductPrice()*ci.getQuantity();
        }
        cart.setTotalPrice(total);
        cartRepository.saveAndFlush(cart);
        return "Cart saved";
    }

    private Cart createCart() {
        Cart userCart  = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if(userCart != null){
            return userCart;
        }

        Cart cart = new Cart();
        cart.setTotalPrice(0.00);
        cart.setUser(authUtil.loggedInUser());
        Cart newCart =  cartRepository.save(cart);

        return newCart;
    }
}
