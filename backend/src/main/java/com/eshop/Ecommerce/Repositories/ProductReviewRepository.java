package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview,Long> {
    Optional<ProductReview> findByUser_UseridAndProduct_ProductId(Long userId,Long productId);
    List<ProductReview> findTop30ByProduct_ProductIdOrderByUpdatedAtDesc(Long productId);
}
