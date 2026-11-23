package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.AfterSaleRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AfterSaleRequestRepository extends JpaRepository<AfterSaleRequest,Long> {
    List<AfterSaleRequest> findByCustomer_UseridOrderByCreatedAtDesc(Long userId);
    List<AfterSaleRequest> findTop100ByStatusOrderByCreatedAtAsc(String status);
    boolean existsByOrder_OrderIdAndTypeAndStatus(Long orderId,String type,String status);
}
