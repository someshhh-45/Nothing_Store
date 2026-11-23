package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name="product_reviews", uniqueConstraints=@UniqueConstraint(name="uq_customer_product_review",columnNames={"user_id","product_id"}))
@Getter @Setter @NoArgsConstructor
public class ProductReview {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false)
    private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="product_id",nullable=false)
    private Product product;
    @Column(nullable=false)
    private int rating;
    @Column(nullable=false,length=1000)
    private String comment;
    @Column(nullable=false)
    private Instant createdAt=Instant.now();
    @Column(nullable=false)
    private Instant updatedAt=Instant.now();
}
