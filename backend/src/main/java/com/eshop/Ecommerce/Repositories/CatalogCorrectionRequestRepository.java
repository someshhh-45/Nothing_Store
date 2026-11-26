package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.CatalogCorrectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CatalogCorrectionRequestRepository extends JpaRepository<CatalogCorrectionRequest,Long> {
 List<CatalogCorrectionRequest> findBySellerIdOrderByRequestedAtDesc(Long sellerId);
 List<CatalogCorrectionRequest> findAllByOrderByRequestedAtDesc();
 boolean existsByProductIdAndStatusIn(Long productId,java.util.Collection<String> statuses);
}
