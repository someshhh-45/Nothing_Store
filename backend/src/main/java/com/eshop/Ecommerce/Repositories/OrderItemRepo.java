package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepo extends JpaRepository<OrderItem, Long> {
    @org.springframework.data.jpa.repository.Query(
       value="select i from OrderItem i join fetch i.product p join fetch i.order o where p.user.userid=:sellerId order by o.orderId desc",
       countQuery="select count(i) from OrderItem i where i.product.user.userid=:sellerId")
    org.springframework.data.domain.Page<OrderItem> findSellerOrderLines(
       @org.springframework.data.repository.query.Param("sellerId") Long sellerId,
       org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
       "select count(distinct i.order.orderId),coalesce(sum(i.quantity),0L),coalesce(sum(i.orderedProductPrice*i.quantity),0.0) " +
       "from OrderItem i where i.product.user.userid=:sellerId")
    java.util.List<Object[]> sellerOrderStats(@org.springframework.data.repository.query.Param("sellerId") Long sellerId);

    @org.springframework.data.jpa.repository.Query("select count(i) from OrderItem i where lower(i.order.email)=lower(:email) and i.product.productId=:productId")
    long countPurchasedByEmailAndProduct(@org.springframework.data.repository.query.Param("email") String email,
        @org.springframework.data.repository.query.Param("productId") Long productId);
}
