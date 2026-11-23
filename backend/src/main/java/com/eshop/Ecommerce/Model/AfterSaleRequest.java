package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name="after_sale_requests", indexes=@Index(name="idx_after_sale_customer",columnList="customer_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class AfterSaleRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="order_id",nullable=false)
    private Order order;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="customer_id",nullable=false)
    private User customer;
    @Column(nullable=false,length=20)
    private String type;
    @Column(nullable=false,length=20)
    private String status="PENDING";
    @Column(nullable=false,length=500)
    private String reason;
    private LocalDate requestedDate;
    @Column(name="created_at",nullable=false,updatable=false)
    private Instant createdAt=Instant.now();
    private Instant reviewedAt;
    @Column(length=60)
    private String reviewedBy;
    @Column(length=500)
    private String reviewNote;
}
