package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SellerProfileRepository extends JpaRepository<SellerProfile,Long> {
 Optional<SellerProfile> findByUser_Userid(Long userId);
 List<SellerProfile> findByStatusOrderBySubmittedAtAsc(String status);
}
