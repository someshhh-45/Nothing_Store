package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.DeliveryCoverageZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryCoverageZoneRepository extends JpaRepository<DeliveryCoverageZone,Long> {
 List<DeliveryCoverageZone> findBySellerKeyOrderByPinFromAsc(Long sellerKey);
 long countBySellerKeyAndEnabledTrue(Long sellerKey);
}
