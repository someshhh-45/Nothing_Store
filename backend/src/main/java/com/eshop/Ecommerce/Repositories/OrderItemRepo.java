package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepo extends JpaRepository<OrderItem, Long> {
    @org.springframework.data.jpa.repository.Query("select count(i) from OrderItem i where lower(i.order.email)=lower(:email) and i.product.productId=:productId")
    long countPurchasedByEmailAndProduct(@org.springframework.data.repository.query.Param("email") String email,
        @org.springframework.data.repository.query.Param("productId") Long productId);
}
