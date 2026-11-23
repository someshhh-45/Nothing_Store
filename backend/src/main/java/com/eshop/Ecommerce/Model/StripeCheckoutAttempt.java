package com.eshop.Ecommerce.Model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Getter @Setter
@Table(name="stripe_checkout_attempts",uniqueConstraints=@UniqueConstraint(name="uk_stripe_session",columnNames="session_id"))
public class StripeCheckoutAttempt {
 @Id @Column(length=36) private String id;
 @Column(name="session_id",length=255,unique=true) private String sessionId;
 @Column(nullable=false) private String email;
 @Column(nullable=false) private Long addressId;
 @Column(nullable=false) private long expectedAmount;
 @Column(nullable=false,length=4000) private String cartFingerprint;
 // Values snapshotted before redirect; webhook revalidates against the live cart.
 @Column(length=32) private String offerCode;
 private Long discountPaise;
 @Column(nullable=false,length=20) private String status="PENDING";
 private Long orderId;
 @Column(nullable=false) private Instant createdAt=Instant.now();
}
