package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepo extends JpaRepository<Product,Long> , JpaSpecificationExecutor<Product> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.productId=:id")
    java.util.Optional<Product> findByIdForInventoryUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Product p set p.quantity = p.quantity - :qty where p.productId = :id and p.quantity >= :qty")
    int reserveStock(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("qty") int qty);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Product p set p.quantity = p.quantity + :qty where p.productId = :id")
    int releaseStock(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("qty") int qty);

    Page<Product> findByCategoryOrderByPriceAsc(Category category, Pageable pageDetails);

    Page<Product> findByProductNameLikeIgnoreCase(String keyword, Pageable pageDetails);


    Page<Product> findByUser(User user,Pageable pageDetails);
}
