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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Each seller can update only the part of a customer order containing their own listings. */
@RestController
public class SellerFulfillmentController {
 private final OrderRepo orders;
 private final SellerOrderFulfillmentRepository fulfillments;
 private final AuthUtil auth;
 public SellerFulfillmentController(OrderRepo orders,SellerOrderFulfillmentRepository fulfillments,AuthUtil auth){
  this.orders=orders;this.fulfillments=fulfillments;this.auth=auth;
 }
 public record Update(@NotBlank String stage,@Size(max=180) String note,
     @Size(max=80) String carrier,
     @Pattern(regexp="[A-Za-z0-9-]{5,60}") String trackingNumber){}
 public record View(Long orderId,Long sellerId,String sellerName,String stage,
     String carrier,String trackingNumber,String note,OffsetDateTime updatedAt){}

 private static boolean owns(Order order,Long sellerId){
  return order.getOrderItems().stream().anyMatch(i->i.getProduct()!=null &&
     i.getProduct().getUser()!=null &&
     sellerId.equals(i.getProduct().getUser().getUserid()));
 }
 private static View view(Order o,Long id,SellerOrderFulfillment f){
  return new View(o.getOrderId(),id,null,f==null?"PLACED":f.getStage(),
     f==null?null:f.getCarrier(),f==null?null:f.getTrackingNumber(),
     f==null?null:f.getNote(),f==null?null:f.getUpdatedAt());
 }

 @GetMapping("/api/seller/orders/{orderId}/fulfillment")
 @Transactional(readOnly=true)
 public View mine(@PathVariable Long orderId){
  Order order=orders.findById(orderId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  Long sellerId=auth.loggedInUserId();
  if(!owns(order,sellerId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
  return view(order,sellerId,fulfillments.findByOrderIdAndSellerId(orderId,sellerId).orElse(null));
 }

 @PutMapping("/api/seller/orders/{orderId}/fulfillment")
 @Transactional
 public View update(@PathVariable Long orderId,@Valid @RequestBody Update input){
  // Lock the order row so two concurrent first-time updates cannot create duplicate seller states.
  Order order=orders.findByIdForTrackingUpdate(orderId)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  Long sellerId=auth.loggedInUserId();
  if(!owns(order,sellerId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
  if("CANCELLED".equalsIgnoreCase(order.getOrderStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancelled orders cannot be fulfilled");
  SellerOrderFulfillment state=fulfillments.findByOrderIdAndSellerId(orderId,sellerId).orElse(null);
  OrderTrackingStage current=OrderTrackingStage.parse(state==null?"PLACED":state.getStage());
  OrderTrackingStage next=OrderTrackingStage.parse(input.stage());
  if(next==OrderTrackingStage.CANCELLED || !current.canAdvanceTo(next))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Advance fulfillment through the next stage only");
  if(next!=OrderTrackingStage.SHIPPED &&
      (input.carrier()!=null || input.trackingNumber()!=null))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Courier reference is only accepted when shipping");
  if(next==OrderTrackingStage.SHIPPED &&
      (input.carrier()==null || input.carrier().isBlank() ||
       input.trackingNumber()==null || input.trackingNumber().isBlank()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter carrier and tracking number together");
  if(state==null){state=new SellerOrderFulfillment();state.setOrderId(orderId);state.setSellerId(sellerId);}
  state.setStage(next.name());
  state.setNote(input.note()==null?null:input.note().trim());
  if(next==OrderTrackingStage.SHIPPED){
   state.setCarrier(input.carrier().trim());state.setTrackingNumber(input.trackingNumber().trim());
  }
  state.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
  return view(order,sellerId,fulfillments.saveAndFlush(state));
 }

 @GetMapping("/api/customer/orders/{orderId}/seller-fulfillments")
 @Transactional(readOnly=true)
 public List<View> customer(@PathVariable Long orderId){
  Order order=orders.findById(orderId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  if(!order.getEmail().equalsIgnoreCase(auth.loggedInEmail()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
  Map<Long,String> participants=new LinkedHashMap<>();
  for(OrderItem item:order.getOrderItems()){
   if(item.getProduct()==null||item.getProduct().getUser()==null)continue;
   User seller=item.getProduct().getUser();
   participants.put(seller.getUserid(),seller.getUserName());
  }
  Map<Long,SellerOrderFulfillment> states=new HashMap<>();
  for(SellerOrderFulfillment f:fulfillments.findByOrderId(orderId))
   states.put(f.getSellerId(),f);
  List<View> result=new ArrayList<>();
  for(var entry:participants.entrySet()){
   View v=view(order,entry.getKey(),states.get(entry.getKey()));
   result.add(new View(v.orderId(),v.sellerId(),entry.getValue(),v.stage(),
     v.carrier(),v.trackingNumber(),v.note(),v.updatedAt()));
  }
  return result;
 }
}
