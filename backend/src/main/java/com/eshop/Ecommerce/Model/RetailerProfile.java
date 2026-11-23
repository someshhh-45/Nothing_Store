package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * Retailer is a wholesale *buyer*, not a product seller. GSTIN submission never
 * self-grants a discount. Approval requires a trusted ROLE_ADMIN.
 */
@Entity @Table(name="retailer_profiles", uniqueConstraints={
    @UniqueConstraint(name="uk_retailer_gstin",columnNames="gstin"),
    @UniqueConstraint(name="uk_retailer_user",columnNames="user_id")
})
@Getter @Setter
public class RetailerProfile {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;
 @OneToOne(optional=false,fetch=FetchType.LAZY)
 @JoinColumn(name="user_id",nullable=false)
 private User user;
 @Column(nullable=false,length=100)
 private String businessName;
 @Column(nullable=false,length=15)
 private String gstin;
 @Column(nullable=false,length=16)
 private String status="PENDING";
 @Column(length=500)
 private String reviewNote;
 private Instant submittedAt=Instant.now();
 private Instant reviewedAt;
 private String reviewedBy;
}
