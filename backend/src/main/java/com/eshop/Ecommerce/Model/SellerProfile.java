package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="seller_profiles")
@Getter @Setter
public class SellerProfile {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;
 @OneToOne(optional=false,fetch=FetchType.LAZY)
 @JoinColumn(name="user_id",nullable=false,unique=true)
 private User user;
 @Column(nullable=false,length=120) private String businessName;
 @Column(nullable=false,length=15) private String contactPhone;
 @Column(length=15) private String gstin;
 @Column(nullable=false,length=16) private String status="PENDING";
 @Column(nullable=false) private Instant submittedAt=Instant.now();
 private Instant reviewedAt;
 @Column(length=100) private String reviewedBy;
 @Column(length=500) private String reviewNote;
}
