package com.eshop.Ecommerce.Repositories;
import com.eshop.Ecommerce.Model.RetailerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface RetailerProfileRepository extends JpaRepository<RetailerProfile,Long>{
 Optional<RetailerProfile> findByUser_Userid(Long userid);
 boolean existsByGstinIgnoreCase(String gstin);
 List<RetailerProfile> findByStatusOrderBySubmittedAtAsc(String status);
}
