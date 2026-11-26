package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Getter @Setter
@Table(name="catalog_correction_requests")
public class CatalogCorrectionRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;
 @Column(name="product_id",nullable=false) private Long productId;
 @Column(name="seller_id",nullable=false) private Long sellerId;
 @Column(nullable=false,length=24) private String kind;
 @Column(nullable=false,length=24) private String status="REQUESTED";
 @Column(nullable=false,length=500) private String message;
 @Column(name="seller_note",length=500) private String sellerNote;
 @Column(name="review_note",length=500) private String reviewNote;
 @Column(name="original_image",length=2048) private String originalImage;
 @Column(name="original_description",length=255) private String originalDescription;
 @Column(name="original_name",length=255) private String originalName;
 @Column(name="original_brand",length=90) private String originalBrand;
 @Column(name="requested_by",nullable=false,length=80) private String requestedBy;
 @Column(name="requested_at",nullable=false) private OffsetDateTime requestedAt;
 @Column(name="submitted_at") private OffsetDateTime submittedAt;
 @Column(name="reviewed_by",length=80) private String reviewedBy;
 @Column(name="reviewed_at") private OffsetDateTime reviewedAt;
}
