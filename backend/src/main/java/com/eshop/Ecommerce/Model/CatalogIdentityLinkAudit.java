package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Getter @Setter
@Table(name="catalog_identity_link_audit")
public class CatalogIdentityLinkAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="product_id",nullable=false) private Long productId;
 @Column(name="canonical_product_id",nullable=false) private Long canonicalProductId;
 @Column(name="linked_by",nullable=false,length=80) private String linkedBy;
 @Column(nullable=false,length=500) private String reason;
 @Column(name="linked_at",nullable=false) private OffsetDateTime linkedAt;
}
