package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** One independently tracked fulfillment per seller within a customer order. */
@Entity
@Getter @Setter
@Table(name="seller_order_fulfillments",
 uniqueConstraints=@UniqueConstraint(name="uk_seller_order_fulfillment",columnNames={"order_id","seller_id"}))
public class SellerOrderFulfillment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;
 @Column(name="order_id",nullable=false) private Long orderId;
 @Column(name="seller_id",nullable=false) private Long sellerId;
 @Column(nullable=false,length=32) private String stage="PLACED";
 @Column(length=80) private String carrier;
 @Column(name="tracking_number",length=60) private String trackingNumber;
 @Column(length=180) private String note;
 @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt=OffsetDateTime.now(ZoneOffset.UTC);
}
