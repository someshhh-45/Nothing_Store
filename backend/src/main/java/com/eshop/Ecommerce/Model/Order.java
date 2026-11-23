package com.eshop.Ecommerce.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Email
    @Column(nullable = false)
    private String email;

    @OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<OrderItem> orderItems = new ArrayList<>();

    private LocalDate orderDate;

    @OneToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private Double totalAmount;
    private Double orderSubtotal;
    private Double discountAmount;
    @Column(length=32) private String offerCode;
    private String orderStatus;

    // Snapshot captured at purchase time so later address edits do not rewrite receipts.
    @Column(name="shipping_address_snapshot",length=1000)
    private String shippingAddressSnapshot;

    // Optional, manually entered fulfillment data; not an active courier integration.
    @Column(length=80) private String shippingCarrier;
    @Column(length=90) private String shippingTrackingNumber;
    private LocalDate estimatedDeliveryDate;

    // Reference to Address
    @ManyToOne
    @JoinColumn(name = "address_id")
    private Address address;
}