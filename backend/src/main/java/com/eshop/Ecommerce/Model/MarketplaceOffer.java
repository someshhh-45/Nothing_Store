package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/** Monetary coupons and automatic spend-threshold campaigns. Values are INR. */
@Entity
@Table(name="marketplace_offers",uniqueConstraints=@UniqueConstraint(name="uk_marketplace_offer_code",columnNames="code"))
@Getter @Setter @NoArgsConstructor
public class MarketplaceOffer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=32,unique=true) private String code;
 @Column(nullable=false,length=90) private String title;
 @Column(length=300) private String description;
 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=16) private Kind kind;
 public enum Kind { PERCENT, FLAT }
 @Column(nullable=false,precision=12,scale=2) private BigDecimal value;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal minSubtotal=BigDecimal.ZERO;
 @Column(precision=12,scale=2) private BigDecimal maxSubtotal;
 @Column(precision=12,scale=2) private BigDecimal maxDiscount;
 @Column(nullable=false) private Instant startsAt;
 @Column(nullable=false) private Instant endsAt;
 @Column(nullable=false) private Integer globalLimit=100000;
 @Column(nullable=false) private Integer perCustomerLimit=1;
 @Column(nullable=false) private Integer usedCount=0;
 @Column(nullable=false) private boolean autoApply=false;
 @Column(nullable=false) private boolean active=true;
}
