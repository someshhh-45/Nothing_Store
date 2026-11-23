package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name="customer_wishlist", uniqueConstraints=@UniqueConstraint(name="uq_customer_wishlist", columnNames={"user_id","product_id"}))
@Getter @Setter @NoArgsConstructor
public class WishlistEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false)
    private User user;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="product_id", nullable=false)
    private Product product;
    @Column(nullable=false, updatable=false)
    private Instant createdAt=Instant.now();
}
