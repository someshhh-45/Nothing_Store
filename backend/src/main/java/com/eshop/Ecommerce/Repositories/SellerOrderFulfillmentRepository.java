package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.SellerOrderFulfillment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SellerOrderFulfillmentRepository extends JpaRepository<SellerOrderFulfillment,Long> {
 Optional<SellerOrderFulfillment> findByOrderIdAndSellerId(Long orderId,Long sellerId);
 List<SellerOrderFulfillment> findByOrderId(Long orderId);
}
