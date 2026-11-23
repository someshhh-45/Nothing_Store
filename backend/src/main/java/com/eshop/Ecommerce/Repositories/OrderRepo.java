package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrderRepo extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId=:id")
    Optional<Order> findByIdForTrackingUpdate(@Param("id") Long id);

    java.util.List<Order> findByEmailIgnoreCaseOrderByOrderDateDesc(String email);
    @Query("SELECT COALESCE(SUM (o.totalAmount),0) FROM Order o")
    Double getTotalRevenue();

    long countByEmailIgnoreCaseAndOfferCodeIgnoreCase(String email, String offerCode);
}
