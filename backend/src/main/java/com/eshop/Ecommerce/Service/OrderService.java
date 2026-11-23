package com.eshop.Ecommerce.Service;

import com.eshop.Ecommerce.Payload.OrderDTO;
import com.eshop.Ecommerce.Payload.OrderResponse;
import jakarta.transaction.Transactional;

public interface OrderService {
    @Transactional
    OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage);

    // Internal-only path: invoked after a verified Stripe webhook.
    OrderDTO placeVerifiedStripeOrder(String emailId, Long addressId, String stripeSessionId);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder(Long orderId, String status);

    OrderDTO updateSellerOrder(Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
