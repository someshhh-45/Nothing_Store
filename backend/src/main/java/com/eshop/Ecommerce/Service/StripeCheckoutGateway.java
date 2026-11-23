package com.eshop.Ecommerce.Service;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Util.AuthUtil;
import com.eshop.Ecommerce.Payload.OrderDTO;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.RequestOptions;
import com.stripe.exception.StripeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class StripeCheckoutGateway {
 private final CartRepository carts;
 private final AddressRepository addresses;
 private final StripeCheckoutAttemptRepository attempts;
 private final AuthUtil auth;
 private final OrderService orders;
 private final CartOfferService offerPricing;
 private final String key,webhookSecret,successUrl,cancelUrl;
 private final ObjectMapper json;

 public StripeCheckoutGateway(CartRepository carts,AddressRepository addresses,
  StripeCheckoutAttemptRepository attempts,AuthUtil auth,OrderService orders,CartOfferService offerPricing,ObjectMapper json,
  @Value("${stripe.secret.key:}") String key,
  @Value("${stripe.webhook.secret:}") String webhookSecret,
  @Value("${stripe.success.url}") String successUrl,
  @Value("${stripe.cancel.url}") String cancelUrl){
  this.carts=carts;this.addresses=addresses;this.attempts=attempts;
  this.auth=auth;this.orders=orders;this.offerPricing=offerPricing;this.json=json;
  this.key=key;this.webhookSecret=webhookSecret;this.successUrl=successUrl;this.cancelUrl=cancelUrl;
 }

 public boolean enabled(){
  return key.startsWith("sk_") && webhookSecret.startsWith("whsec_");
 }

 private static long cents(double amount){
  return BigDecimal.valueOf(amount).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();
 }
 private static String fingerprint(List<CartItem> items){
  return items.stream().sorted(Comparator.comparing(i->i.getProduct().getProductId()))
   .map(i->i.getProduct().getProductId()+":"+i.getQuantity()+":"+cents(i.getProductPrice()))
   .reduce("",(a,b)->a+b+";");
 }

 private static String quotedFingerprint(List<CartItem> items,CartOfferService.Quote quote){
  return fingerprint(items)+(quote.applied()?"offer:"+quote.code()+":"+CartOfferService.paise(quote.discount())+";":"");
 }

 public record CheckoutLink(String url,String sessionId){}
 public record CheckoutStatus(String status,Long orderId){}

 // Creates a Stripe-hosted payment page using ONLY server-owned product prices,
 // stock and the currently authenticated shopper's delivery address.
 @Transactional
 public CheckoutLink create(Long addressId)throws StripeException{
  if(!enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
     "Stripe is not configured. Set STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET.");
  User buyer=auth.loggedInUser();
  Address address=addresses.findById(addressId)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery address not found"));
  if(address.getUser()==null || !buyer.getUserid().equals(address.getUser().getUserid()))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Address does not belong to buyer");
  Cart cart=carts.findCartByEmail(buyer.getEmail());
  if(cart==null||cart.getCartItems().isEmpty())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cart is empty");
  List<CartItem> items=new ArrayList<>(cart.getCartItems());
  CartOfferService.Quote quote=offerPricing.quote(cart,buyer.getEmail(),true);
  boolean discounted=quote.applied()&&quote.discount()>0;
  SessionCreateParams.Builder builder=SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.PAYMENT)
      .setSuccessUrl(successUrl+(successUrl.contains("?")?"&":"?")+"session_id={CHECKOUT_SESSION_ID}")
      .setCancelUrl(cancelUrl)
      .setCustomerEmail(buyer.getEmail())
      .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
  long total=0;
  for(CartItem item:items){
   if(item.getQuantity()==null||item.getQuantity()<1||
       item.getProduct().getQuantity()==null||
       item.getProduct().getQuantity()<item.getQuantity())
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock changed; refresh your cart");
   long unit=cents(item.getProductPrice());
   if(unit<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid product price");
   total=Math.addExact(total,Math.multiplyExact(unit,item.getQuantity()));
   if(!discounted)builder.addLineItem(SessionCreateParams.LineItem.builder()
    .setQuantity(item.getQuantity().longValue())
    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
       .setCurrency("inr").setUnitAmount(unit)
       .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
          .setName(item.getProduct().getProductName()+
            (item.getProduct().getVariantLabel()==null?"":" — "+item.getProduct().getVariantLabel())).build())
       .build()).build());
  }
  if(discounted){
   // Stripe Checkout charges the verified final order amount. A single order
   // summary item avoids fractional-paise unit allocation across mixed quantities.
   // Product line items remain independently recorded in PostgreSQL.
   total=quote.totalPaise();
   if(total<100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
      "Discounted order total is too small for online payment; choose COD");
   builder.addLineItem(SessionCreateParams.LineItem.builder()
     .setQuantity(1L)
     .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
       .setCurrency("inr").setUnitAmount(total)
       .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
         .setName("NOTHING Market order — "+quote.code()+" offer applied").build())
       .build()).build());
  } else if(total!=quote.totalPaise()) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Basket totals changed; refresh the cart");
  }
  StripeCheckoutAttempt attempt=new StripeCheckoutAttempt();
  attempt.setId(UUID.randomUUID().toString());
  attempt.setEmail(buyer.getEmail());attempt.setAddressId(addressId);
  attempt.setExpectedAmount(total);
  attempt.setCartFingerprint(quotedFingerprint(items,quote));
  attempt.setOfferCode(quote.applied()?quote.code():null);
  attempt.setDiscountPaise(CartOfferService.paise(quote.discount()));
  attempts.saveAndFlush(attempt);
  builder.putMetadata("checkoutAttemptId",attempt.getId());
  // Never pass the client a mutable amount or a secret key.
  RequestOptions options=RequestOptions.builder().setApiKey(key)
      .setIdempotencyKey(attempt.getId()).build();
  Session session=Session.create(builder.build(),options);
  if(session.getUrl()==null || session.getId()==null)
   throw new IllegalStateException("Stripe did not return a checkout URL");
  attempt.setSessionId(session.getId());
  attempts.save(attempt);
  return new CheckoutLink(session.getUrl(),session.getId());
 }

 @Transactional(readOnly=true)
 public CheckoutStatus status(String sessionId){
  User buyer=auth.loggedInUser();
  // Only the logged-in buyer can inspect their own checkout attempts.
  return attempts.findBySessionIdAndEmailIgnoreCase(sessionId,buyer.getEmail())
   .map(s->new CheckoutStatus(s.getStatus(),s.getOrderId()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Checkout not found"));
 }

 @Transactional
 public void fulfillPaidCheckout(String payload)throws Exception{
  JsonNode session=json.readTree(payload).path("data").path("object");
  String id=session.path("id").asText();
  String attemptId=session.path("metadata").path("checkoutAttemptId").asText();
  if(id.isBlank()||attemptId.isBlank())throw new IllegalArgumentException("Missing Stripe session metadata");
  StripeCheckoutAttempt attempt=attempts.lockById(attemptId)
    .orElseThrow(()->new IllegalArgumentException("Unknown checkout attempt"));
  if("COMPLETED".equals(attempt.getStatus()))return; // replay-safe
  if(!"PENDING".equals(attempt.getStatus()))throw new IllegalStateException("Unexpected checkout state");
  if(!id.equals(attempt.getSessionId()))throw new IllegalArgumentException("Stripe session mismatch");
  if(!"paid".equals(session.path("payment_status").asText()))
   throw new IllegalArgumentException("Stripe session has not been paid");
  if(!"inr".equalsIgnoreCase(session.path("currency").asText()))
   throw new IllegalArgumentException("Unexpected Stripe currency");
  if(session.path("amount_total").asLong(-1)!=attempt.getExpectedAmount())
   throw new IllegalArgumentException("Stripe payment amount mismatch");
  Cart cart=carts.findCartByEmail(attempt.getEmail());
  if(cart==null)throw new IllegalStateException("Customer cart was removed; requires manual payment reconciliation.");
  CartOfferService.Quote current=offerPricing.quote(cart,attempt.getEmail(),true);
  if(current.totalPaise()!=attempt.getExpectedAmount()||
      !quotedFingerprint(new ArrayList<>(cart.getCartItems()),current).equals(attempt.getCartFingerprint()))
    throw new IllegalStateException("Cart or offer changed after payment; requires manual payment reconciliation.");
  // OrderService reserves stock atomically and commits Kafka outbox events
  // within the same transaction as marking the attempt completed.
  OrderDTO order=orders.placeVerifiedStripeOrder(attempt.getEmail(),attempt.getAddressId(),id);
  attempt.setOrderId(order.getOrderId());attempt.setStatus("COMPLETED");attempts.save(attempt);
 }
}
