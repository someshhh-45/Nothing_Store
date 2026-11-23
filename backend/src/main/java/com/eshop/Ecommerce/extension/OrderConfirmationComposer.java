package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Order;
import com.eshop.Ecommerce.Model.OrderItem;
import com.eshop.Ecommerce.Repositories.OrderRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a stable, escaped receipt from committed order facts, never client prices.
 * JPA lazy order items are copied inside the read-only transaction.
 */
@Service
public class OrderConfirmationComposer {
 private final OrderRepo orders;
 public OrderConfirmationComposer(OrderRepo orders){this.orders=orders;}
 public record Confirmation(String to,String subject,String plainText,String htmlText){}

 @Transactional(readOnly=true)
 public Confirmation compose(Long orderId) {
  Order order=orders.findById(orderId)
   .orElseThrow(()->new IllegalStateException("Order not found for confirmation"));
  if(order.getEmail()==null||order.getEmail().isBlank())
   throw new IllegalStateException("Order has no email recipient");
  boolean card=order.getPayment()!=null&&
    "STRIPE".equalsIgnoreCase(order.getPayment().getPgName());
  String payment=card?"Card payment received (Stripe)":"Cash on delivery — payment due at delivery";
  String address=order.getShippingAddressSnapshot();
  if(address==null||address.isBlank()){
   var a=order.getAddress();
   address=a==null?"Saved delivery address":String.join(", ",
    nonNull(a.getBuildingName()),nonNull(a.getStreet()),nonNull(a.getCity()),
    nonNull(a.getState()),nonNull(a.getPincode()),nonNull(a.getCountry()));
  }
  String subject="NOTHING Market | Order #"+orderId+" confirmed";
  StringBuilder text=new StringBuilder("THANK YOU FOR SHOPPING AT NOTHING MARKET\n\n");
  text.append("Order #").append(orderId).append("\n")
      .append("Status: Confirmed\n")
      .append("Payment: ").append(payment).append("\n\n")
      .append("ITEMS\n");
  StringBuilder lines=new StringBuilder();
  List<OrderItem> items=new ArrayList<>(order.getOrderItems());
  for(OrderItem item:items){
   String label=item.getOrderedProductName();
   if(label==null||label.isBlank())
    label=item.getProduct()==null?"Product":item.getProduct().getProductName();
   int qty=item.getQuantity()==null?0:item.getQuantity();
   String lineTotal=inr(BigDecimal.valueOf(item.getOrderedProductPrice())
      .multiply(BigDecimal.valueOf(qty)));
   text.append(qty).append(" x ").append(label).append(" — ").append(lineTotal).append("\n");
   lines.append("<tr><td style='padding:9px 8px;border-bottom:1px solid #edf0ed'>")
      .append(escape(label)).append("</td><td style='padding:9px 8px;text-align:center;border-bottom:1px solid #edf0ed'>")
      .append(qty).append("</td><td style='padding:9px 8px;text-align:right;border-bottom:1px solid #edf0ed'>")
      .append(escape(lineTotal)).append("</td></tr>");
  }
  if(order.getOrderSubtotal()!=null&&order.getDiscountAmount()!=null
     &&order.getDiscountAmount()>0){
   text.append("\nSubtotal: ").append(inr(order.getOrderSubtotal()))
    .append("\nDiscount").append(order.getOfferCode()==null?"":" ("+order.getOfferCode()+")")
    .append(": -").append(inr(order.getDiscountAmount()));
  }
  text.append("\nTOTAL: ").append(inr(order.getTotalAmount()))
   .append("\n\nDeliver to: ").append(address)
   .append("\n\nView order details and tracking from your NOTHING Market account.")
   .append("\nTracking milestones are updated by store staff; live courier tracking is not available.")
   .append("\n\nIf you did not place this order, contact store support.");
  String summary=(order.getDiscountAmount()!=null&&order.getDiscountAmount()>0)
   ?"<p>Subtotal: "+escape(inr(order.getOrderSubtotal()))
     +"<br><span style='color:#167344'>Offer "+escape(nonNull(order.getOfferCode()))
     +": -"+escape(inr(order.getDiscountAmount()))+"</span></p>":"";
  String html="<!doctype html><html><body style='background:#f5f7f2;margin:0;padding:28px 12px;font-family:Arial,Helvetica,sans-serif;color:#142319'>"+
   "<main style='max-width:620px;margin:auto;padding:30px;background:white;border:1px solid #e0e8dc'>"+
   "<p style='font-size:12px;font-weight:900;letter-spacing:3px'>NOTHING MARKET</p>"+
   "<h1 style='font-size:29px;margin:12px 0;color:#142f20'>Your order is confirmed.</h1>"+
   "<p>Thank you for your purchase! Your order <b>#"+orderId+"</b> has been placed.</p>"+
   "<p style='background:#ecf6e8;padding:13px;border-radius:6px'><b>Payment:</b> "+escape(payment)+"</p>"+
   "<table role='presentation' style='width:100%;border-collapse:collapse;font-size:14px'>"+
   "<thead><tr><th style='text-align:left'>Item</th><th>Qty</th><th style='text-align:right'>Amount</th></tr></thead><tbody>"+
   lines+"</tbody></table>"+summary+
   "<p style='font-size:21px;font-weight:bold'>Total: "+escape(inr(order.getTotalAmount()))+"</p>"+
   "<p><b>Delivery address</b><br>"+escape(address)+"</p>"+
   "<p>Track your order from your NOTHING Market account.</p>"+
   "<p style='font-size:12px;color:#657568'>Tracking milestones are updated by store staff, not real-time courier GPS. If you did not place this order, contact store support.</p>"+
   "</main></body></html>";
  return new Confirmation(order.getEmail(),subject,text.toString(),html);
 }
 private static String nonNull(String s){return s==null?"":s;}
 private static String inr(Double amount){return inr(BigDecimal.valueOf(amount==null?0:amount));}
 private static String inr(BigDecimal amount){return "INR "+amount.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 private static String escape(String s){
  if(s==null)return "";
  return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
   .replace("\"","&quot;").replace("'","&#39;");
 }
}
