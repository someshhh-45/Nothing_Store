package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.MarketplaceOffer;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface MarketplaceOfferRepository extends JpaRepository<MarketplaceOffer,Long> {
 Optional<MarketplaceOffer> findByCodeIgnoreCase(String code);
 List<MarketplaceOffer> findByActiveTrueOrderByMinSubtotalAsc();
 List<MarketplaceOffer> findAllByOrderByIdDesc();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from MarketplaceOffer o where o.code=:code")
 Optional<MarketplaceOffer> lockByCode(@Param("code") String code);
}
