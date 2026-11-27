package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Getter @Setter
@Table(name="delivery_coverage_zones")
public class DeliveryCoverageZone {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="seller_key",nullable=false) private Long sellerKey;
 @Column(name="pin_from",nullable=false,length=6) private String pinFrom;
 @Column(name="pin_to",nullable=false,length=6) private String pinTo;
 @Column(name="eta_min_days",nullable=false) private int etaMinDays;
 @Column(name="eta_max_days",nullable=false) private int etaMaxDays;
 @Column(nullable=false) private boolean enabled=true;
 @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
}
