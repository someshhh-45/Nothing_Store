package com.eshop.Ecommerce.Model;

import com.eshop.Ecommerce.extension.OrderTrackingStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name="order_tracking_events", indexes={@Index(name="idx_tracking_order_time", columnList="order_id,occurred_at")})
@Getter @Setter @NoArgsConstructor
public class OrderTrackingEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="order_id", nullable=false)
    private Order order;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=32)
    private OrderTrackingStage stage;
    @Column(name="occurred_at",nullable=false)
    private OffsetDateTime occurredAt=OffsetDateTime.now(ZoneOffset.UTC);
    @Column(length=180)
    private String note;
    @Column(length=20,nullable=false)
    private String updatedBy="SYSTEM";
}
