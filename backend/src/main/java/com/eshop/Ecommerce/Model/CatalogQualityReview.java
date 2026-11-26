package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
@Table(name="catalog_quality_reviews")
public class CatalogQualityReview {
 @Id @Column(name="issue_key",length=72,nullable=false)
 private String issueKey;
 @Column(nullable=false,length=24)
 private String status="OPEN";
 @Column(length=500)
 private String note;
 @Column(name="reviewed_by",nullable=false,length=80)
 private String reviewedBy;
 @Column(name="updated_at",nullable=false)
 private OffsetDateTime updatedAt;
}
