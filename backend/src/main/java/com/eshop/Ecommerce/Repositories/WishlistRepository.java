package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.WishlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WishlistRepository extends JpaRepository<WishlistEntry,Long> {
    List<WishlistEntry> findByUser_UseridOrderByCreatedAtDesc(Long userId);
    boolean existsByUser_UseridAndProduct_ProductId(Long userId, Long productId);
    long deleteByUser_UseridAndProduct_ProductId(Long userId, Long productId);
}
