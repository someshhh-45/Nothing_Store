package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Repositories.OrderRepo;
import com.eshop.Ecommerce.Util.AuthUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/** The buyer may inspect only their own order's confirmation status.
 * Admin endpoints are guarded by /api/admin/** Spring Security rules. */
@RestController
public class OrderConfirmationStatusController {
 private final OrderRepo orders;
 private final JdbcTemplate jdbc;
 private final AuthUtil auth;
 private final boolean enabled;
 public record Status(String status,boolean sendingEnabled,int attempts,Instant sentAt){}
 public OrderConfirmationStatusController(OrderRepo orders,JdbcTemplate jdbc,AuthUtil auth,
    @Value("${nothing.notifications.mail-enabled:false}") boolean enabled){
  this.orders=orders;this.jdbc=jdbc;this.auth=auth;this.enabled=enabled;
 }
 private void requireOrder(Long orderId,boolean customer){
  if(orderId==null||orderId<1)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
  var order=orders.findById(orderId)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  if(customer&&!order.getEmail().equalsIgnoreCase(auth.loggedInEmail()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
 }
 private Status state(Long orderId){
  var rows=jdbc.query(
   "select status,attempts,sent_at from order_confirmation_emails where order_id=?",
   (rs,n)->new Status(rs.getString("status"),enabled,rs.getInt("attempts"),
    rs.getTimestamp("sent_at")==null?null:rs.getTimestamp("sent_at").toInstant()),orderId);
  return rows.isEmpty()?new Status("NOT_SCHEDULED",enabled,0,null):rows.get(0);
 }
 @GetMapping("/api/customer/orders/{id}/confirmation")
 public Status mine(@PathVariable Long id){
  requireOrder(id,true);
  return state(id);
 }
 @GetMapping("/api/admin/orders/{id}/confirmation")
 public Status admin(@PathVariable Long id){
  requireOrder(id,false);
  return state(id);
 }
 @PostMapping("/api/admin/orders/{id}/confirmation/retry")
 public Status retry(@PathVariable Long id){
  requireOrder(id,false);
  // Never resend already accepted email or steal a live replica's SMTP lease.
  int updated=jdbc.update("update order_confirmation_emails set status='PENDING'," +
   "next_attempt_at=now(),claimed_at=null,last_error_code=null where order_id=? and " +
   "(status='PENDING' or (status='SENDING' and claimed_at < now()-interval '2 minutes'))",id);
  if(updated==0){
   Status current=state(id);
   if("SENT".equals(current.status()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Confirmation already sent");
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Email delivery is in progress or unavailable");
  }
  return state(id);
 }
}
