package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Unverified email addresses never enter the actual customer table. */
@Entity
@Table(name="pending_signups",uniqueConstraints={
    @UniqueConstraint(name="uq_pending_signup_username",columnNames="username"),
    @UniqueConstraint(name="uq_pending_signup_email",columnNames="email")
})
@Getter @Setter @NoArgsConstructor
public class PendingSignup {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=20)
    private String username;
    @Column(nullable=false,length=50)
    private String email;
    @Column(nullable=false,length=120)
    private String passwordHash;
    @Column(nullable=false,length=120)
    private String codeHash;
    @Column(nullable=false)
    private Instant expiresAt;
    @Column(nullable=false)
    private Instant resendNotBefore;
    private int failedAttempts;
}
