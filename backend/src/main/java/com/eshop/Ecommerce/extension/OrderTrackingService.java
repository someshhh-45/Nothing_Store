package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.OrderRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Repositories.OrderTrackingEventRepository;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OrderTrackingService {
    private final OrderRepo orders;
    private final OrderTrackingEventRepository events;
    private final AuthUtil auth;
    private final ProductRepo products;
    private final DomainEvents domainEvents;
    public OrderTrackingService(OrderRepo orders, OrderTrackingEventRepository events,
        AuthUtil auth, ProductRepo products, DomainEvents domainEvents){
        this.orders=orders;this.events=events;this.auth=auth;
        this.products=products;this.domainEvents=domainEvents;
    }

    public record UpdateRequest(
        @NotBlank String stage,
        @Size(max=180) String note,
        @Size(max=80) String carrier,
        @Pattern(regexp="[A-Za-z0-9-]{5,60}",message="Tracking number must be 5-60 letters/numbers/hyphens")
        String trackingNumber,
        LocalDate estimatedDeliveryDate
    ) {}
    public record TrackingEvent(String stage,String note,String updatedBy,OffsetDateTime occurredAt){}
    public record Milestone(String stage,String label,boolean completed,OffsetDateTime occurredAt){}
    public record TrackingView(
        Long orderId,String currentStage,String orderStatus,String carrier,
        String trackingNumber,LocalDate estimatedDeliveryDate,boolean liveCarrierTracking,
        List<Milestone> milestones,List<TrackingEvent> history
    ) {}

    /** Called inside the same PostgreSQL transaction that creates the order and deducts stock. */
    public void recordPlaced(Order order) {
        OrderTrackingEvent item=new OrderTrackingEvent();
        item.setOrder(order);item.setStage(OrderTrackingStage.PLACED);
        item.setNote("Order placed");item.setUpdatedBy("SYSTEM");
        events.save(item);
    }

    private Order order(Long id) {
        return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
    }
    private void requireCustomerOwner(Order order) {
        if(!order.getEmail().equalsIgnoreCase(auth.loggedInEmail()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
    }
    private void requireSellerOwner(Order order) {
        Long sellerId=auth.loggedInUser().getUserid();
        boolean ownsProduct=order.getOrderItems().stream().anyMatch(item->
            item.getProduct()!=null && item.getProduct().getUser()!=null &&
            Objects.equals(item.getProduct().getUser().getUserid(), sellerId));
        if(!ownsProduct)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your order to fulfill");
    }

    @Transactional(readOnly=true)
    public TrackingView customerTracking(Long id) {
        Order order=order(id);requireCustomerOwner(order);
        return view(order);
    }
    @Transactional(readOnly=true)
    public TrackingView adminTracking(Long id) { return view(order(id)); }

    private TrackingView view(Order order){
        List<OrderTrackingEvent> entries=events.findByOrderOrderIdOrderByOccurredAtAscIdAsc(order.getOrderId());
        OrderTrackingStage current=entries.isEmpty() ?
            OrderTrackingStage.parse(order.getOrderStatus()) : entries.get(entries.size()-1).getStage();
        List<TrackingEvent> history=entries.stream()
            .map(e->new TrackingEvent(e.getStage().name(), e.getNote(), e.getUpdatedBy(), e.getOccurredAt()))
            .toList();
        var planned=OrderTrackingStage.deliveryStages();
        int currentRank=planned.indexOf(current);
        List<Milestone> milestones=new ArrayList<>();
        for(int index=0;index<planned.size();index++){
            OrderTrackingStage stage=planned.get(index);
            OffsetDateTime occurred=entries.stream()
                .filter(e->e.getStage()==stage).map(OrderTrackingEvent::getOccurredAt).findFirst().orElse(null);
            // Legacy records have no dated events; stage rank is known, timestamps are not.
            milestones.add(new Milestone(stage.name(), stage.name().replace('_',' '),
                current!=OrderTrackingStage.CANCELLED && index<=currentRank, occurred));
        }
        if(current==OrderTrackingStage.CANCELLED){
            OffsetDateTime cancelledAt=entries.stream()
                .filter(e->e.getStage()==OrderTrackingStage.CANCELLED)
                .map(OrderTrackingEvent::getOccurredAt).findFirst().orElse(null);
            milestones.add(new Milestone("CANCELLED","CANCELLED",true,cancelledAt));
        }
        return new TrackingView(order.getOrderId(),current.name(),order.getOrderStatus(),
            order.getShippingCarrier(),order.getShippingTrackingNumber(),
            order.getEstimatedDeliveryDate(),false,
            List.copyOf(milestones),history);
    }

    @Transactional
    public Order updateAdmin(Long id, UpdateRequest request){
        return advance(id,request,false);
    }
    @Transactional
    public Order updateSeller(Long id, UpdateRequest request){
        return advance(id,request,true);
    }
    private Order advance(Long id,UpdateRequest request,boolean seller){
        if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tracking update is required");
        Order order=orders.findByIdForTrackingUpdate(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        if(seller)requireSellerOwner(order);
        List<OrderTrackingEvent> rows=events.findByOrderOrderIdOrderByOccurredAtAscIdAsc(id);
        OrderTrackingStage current=rows.isEmpty() ?
            OrderTrackingStage.parse(order.getOrderStatus()) : rows.get(rows.size()-1).getStage();
        OrderTrackingStage next=OrderTrackingStage.parse(request.stage());
        if(!current.canAdvanceTo(next))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Invalid order transition: "+current+" -> "+next+". Move through stages in order.");
        // A seller is permitted to fulfill, not cancel/refund the shopper's order.
        if(seller && (next==OrderTrackingStage.CANCELLED || next==OrderTrackingStage.CONFIRMED))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seller cannot perform this transition");
        if(request.estimatedDeliveryDate()!=null &&
           request.estimatedDeliveryDate().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estimated delivery must not be in the past");
        if(next!=OrderTrackingStage.SHIPPED && (request.carrier()!=null||request.trackingNumber()!=null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Courier reference may be set only on shipment");
        if(next==OrderTrackingStage.SHIPPED){
            if(request.carrier()!=null && !request.carrier().isBlank())
                order.setShippingCarrier(request.carrier().trim());
            if(request.trackingNumber()!=null && !request.trackingNumber().isBlank())
                order.setShippingTrackingNumber(request.trackingNumber().trim());
            if((order.getShippingCarrier()==null)!=(order.getShippingTrackingNumber()==null))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide carrier and tracking number together");
        }
        if(request.estimatedDeliveryDate()!=null)
            order.setEstimatedDeliveryDate(request.estimatedDeliveryDate());
        if(next==OrderTrackingStage.CANCELLED){
            Payment payment=order.getPayment();
            // Only unpaid COD can be cancelled here. A paid Stripe order needs a
            // verified refund workflow; no shipping status can claim money returned.
            if(payment==null || !"CASH_ON_DELIVERY".equalsIgnoreCase(payment.getPaymentMethod()) ||
               !"PENDING".equalsIgnoreCase(payment.getPgStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Prepaid orders require a verified refund; contact store support");
            for(OrderItem item:order.getOrderItems()){
                if(item.getProduct()==null || item.getQuantity()==null || item.getQuantity()<1 ||
                   products.releaseStock(item.getProduct().getProductId(),item.getQuantity())!=1)
                    throw new IllegalStateException("Could not restore cancelled inventory");
                domainEvents.record("INVENTORY_CHANGED",item.getProduct().getProductId(),order.getOrderId());
            }
        }
        order.setOrderStatus(next.name());
        OrderTrackingEvent event=new OrderTrackingEvent();
        event.setOrder(order);event.setStage(next);event.setNote(request.note()==null?null:request.note().trim());
        event.setUpdatedBy(seller?"SELLER":"ADMIN");
        events.save(event);
        orders.save(order);
        return order;
    }
}
