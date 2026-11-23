package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/**
 * Customer-initiated after-sale requests. Approval is a store workflow,
 * NOT proof that a courier was booked or a Stripe refund was issued.
 */
@RestController
public class AfterSaleController {
    private final AfterSaleRequestRepository requests;
    private final OrderRepo orders;
    private final OrderTrackingService tracking;
    private final OrderTrackingEventRepository history;
    private final AuthUtil auth;

    public AfterSaleController(AfterSaleRequestRepository requests,OrderRepo orders,
        OrderTrackingService tracking,OrderTrackingEventRepository history,AuthUtil auth){
        this.requests=requests;this.orders=orders;this.tracking=tracking;
        this.history=history;this.auth=auth;
    }

    public record NewRequest(@NotBlank @Pattern(regexp="RETURN|RESCHEDULE") String type,
        @NotBlank @Size(min=5,max=500) String reason,LocalDate requestedDate){}
    public record ReviewRequest(@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,
        @Size(max=500) String note){}

    private Map<String,Object> view(AfterSaleRequest r){
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",r.getId());data.put("orderId",r.getOrder().getOrderId());
        data.put("type",r.getType());data.put("reason",r.getReason());
        data.put("requestedDate",r.getRequestedDate());data.put("status",r.getStatus());
        data.put("createdAt",r.getCreatedAt());data.put("reviewedAt",r.getReviewedAt());
        data.put("reviewNote",r.getReviewNote());
        return data;
    }

    @PostMapping("/api/customer/orders/{orderId}/requests")
    @Transactional
    public Map<String,Object> create(@PathVariable Long orderId,@Valid @RequestBody NewRequest request){
        User customer=auth.loggedInUser();
        Order order=orders.findByIdForTrackingUpdate(orderId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        if(!order.getEmail().equalsIgnoreCase(customer.getEmail()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
        if(requests.existsByOrder_OrderIdAndTypeAndStatus(orderId,request.type(),"PENDING"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A request of this type is already pending");

        OrderTrackingStage stage=OrderTrackingStage.valueOf(tracking.customerTracking(orderId).currentStage());
        if("RESCHEDULE".equals(request.type())){
            if(!EnumSet.of(OrderTrackingStage.PLACED,OrderTrackingStage.CONFIRMED,
                OrderTrackingStage.PACKED).contains(stage))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivery date cannot be changed after dispatch");
            if(request.requestedDate()==null || !request.requestedDate().isAfter(LocalDate.now())
                || request.requestedDate().isAfter(LocalDate.now().plusDays(30)))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a future date within 30 days");
        } else {
            if(stage!=OrderTrackingStage.DELIVERED)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Returns can be requested only after delivery");
            boolean recentDelivery=history.findByOrderOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
                .anyMatch(e->e.getStage()==OrderTrackingStage.DELIVERED
                    && e.getOccurredAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusDays(14)));
            if(!recentDelivery)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Return window expired or delivery timestamp unavailable");
            if(request.requestedDate()!=null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Return requests do not accept a delivery date");
        }

        AfterSaleRequest entry=new AfterSaleRequest();
        entry.setOrder(order);entry.setCustomer(customer);entry.setType(request.type());
        entry.setReason(request.reason().trim());entry.setRequestedDate(request.requestedDate());
        return view(requests.save(entry));
    }

    @GetMapping("/api/customer/order-requests")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(){
        return requests.findByCustomer_UseridOrderByCreatedAtDesc(auth.loggedInUserId())
            .stream().map(this::view).toList();
    }

    @GetMapping("/api/admin/order-requests")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> pending(){
        return requests.findTop100ByStatusOrderByCreatedAtAsc("PENDING")
            .stream().map(this::view).toList();
    }

    @PutMapping("/api/admin/order-requests/{id}/review")
    @Transactional
    public Map<String,Object> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){
        AfterSaleRequest entry=requests.findById(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));
        if(!"PENDING".equals(entry.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Request already reviewed");
        Order order=orders.findByIdForTrackingUpdate(entry.getOrder().getOrderId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
        if("APPROVED".equals(request.decision()) && "RESCHEDULE".equals(entry.getType())){
            OrderTrackingStage stage=OrderTrackingStage.valueOf(tracking.adminTracking(order.getOrderId()).currentStage());
            if(!EnumSet.of(OrderTrackingStage.PLACED,OrderTrackingStage.CONFIRMED,
                OrderTrackingStage.PACKED).contains(stage))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Order has shipped; date change cannot be approved");
            if(entry.getRequestedDate()==null || !entry.getRequestedDate().isAfter(LocalDate.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Requested date is no longer in the future");
            order.setEstimatedDeliveryDate(entry.getRequestedDate());
            orders.save(order);
        }
        // A RETURN approval is permission to start a manual return process.
        // Never mark a payment refunded or restock inventory without verified receipt/refund.
        entry.setStatus(request.decision());entry.setReviewNote(request.note());
        entry.setReviewedBy(auth.loggedInUser().getUserName());
        entry.setReviewedAt(Instant.now());
        return view(requests.save(entry));
    }
}
