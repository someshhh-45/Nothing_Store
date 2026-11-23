package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Offline, signed Stripe webhook proves coupon final paid amount and replay safety. */
@SpringBootTest(properties={"stripe.webhook.secret=whsec_offline_offer_test","SEED_OFFERS=false"})
@AutoConfigureMockMvc
class StripeCouponWebhookTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired AddressRepository addresses;
 @Autowired CartRepository carts;
 @Autowired CartItemRepository cartItems;
 @Autowired StripeCheckoutAttemptRepository attempts;
 @Autowired MarketplaceOfferRepository offers;
 @Autowired OrderRepo orders;

 private String sign(String payload)throws Exception{
  long t=Instant.now().getEpochSecond();
  Mac mac=Mac.getInstance("HmacSHA256");
  mac.init(new SecretKeySpec("whsec_offline_offer_test".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return "t="+t+",v1="+HexFormat.of().formatHex(
    mac.doFinal((t+"."+payload).getBytes(StandardCharsets.UTF_8)));
 }

 @Test void signedDiscountedSessionCreatesSingleCorrectlyPricedOrder()throws Exception {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User buyer=users.save(new User("spromo"+suffix,"spromo"+suffix+"@example.org","unused-hash"));
  Category category=new Category();category.setCategoryName("Promo Stripe "+suffix);
  category=categories.save(category);
  Product p=new Product();p.setProductName("Stripe Offer Product "+suffix);
  p.setDescription("Stripe offer integration product for test checkout");
  p.setCategory(category);p.setQuantity(5);p.setPrice(250);p.setSpecialPrice(250);
  p=products.saveAndFlush(p);
  Address address=new Address("Example Avenue Road","Building One A","Bengaluru","Karnataka","India","560001");
  address.setUser(buyer);address=addresses.save(address);
  var offer=new MarketplaceOffer();offer.setCode("STRIPE"+suffix);
  offer.setTitle("Stripe Test Discount");offer.setKind(MarketplaceOffer.Kind.FLAT);
  offer.setValue(new BigDecimal("100"));offer.setMinSubtotal(new BigDecimal("400"));
  offer.setStartsAt(Instant.now().minus(1,ChronoUnit.DAYS));
  offer.setEndsAt(Instant.now().plus(1,ChronoUnit.DAYS));
  offer.setGlobalLimit(1);offer.setPerCustomerLimit(1);offer=offers.saveAndFlush(offer);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(500.0);cart.setCouponCode(offer.getCode());
  cart=carts.saveAndFlush(cart);
  CartItem item=new CartItem();item.setCart(cart);item.setProduct(p);item.setQuantity(2);
  item.setProductPrice(250.0);item.setDiscount(0.0);cartItems.saveAndFlush(item);
  var attempt=new StripeCheckoutAttempt();attempt.setId(UUID.randomUUID().toString());
  attempt.setSessionId("cs_test_coupon_"+suffix);attempt.setEmail(buyer.getEmail());
  attempt.setAddressId(address.getAddressId());attempt.setExpectedAmount(40000);
  attempt.setOfferCode(offer.getCode());attempt.setDiscountPaise(10000L);
  attempt.setCartFingerprint(p.getProductId()+":2:25000;offer:"+offer.getCode()+":10000;");
  attempts.saveAndFlush(attempt);
  String event=json.writeValueAsString(Map.of(
    "id","evt_coupon_"+suffix,"object","event","type","checkout.session.completed",
    "data",Map.of("object",Map.of(
      "id",attempt.getSessionId(),"object","checkout.session","payment_status","paid",
      "currency","inr","amount_total",40000,
      "metadata",Map.of("checkoutAttemptId",attempt.getId())))));
  String signature=sign(event);
  mvc.perform(post("/api/stripe/webhook")
   .contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature",signature)
   .content(event)).andExpect(status().isOk());
  var persisted=attempts.findById(attempt.getId()).orElseThrow();
  assertEquals("COMPLETED",persisted.getStatus());
  var placed=orders.findById(persisted.getOrderId()).orElseThrow();
  assertEquals(500.0,placed.getOrderSubtotal(),0.01);
  assertEquals(100.0,placed.getDiscountAmount(),0.01);
  assertEquals(400.0,placed.getTotalAmount(),0.01);
  assertEquals(offer.getCode(),placed.getOfferCode());
  assertEquals(1,offers.findById(offer.getId()).orElseThrow().getUsedCount());
  assertEquals(3,products.findById(p.getProductId()).orElseThrow().getQuantity());
  mvc.perform(post("/api/stripe/webhook")
   .contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature",signature)
   .content(event)).andExpect(status().isOk());
  assertEquals(1,orders.findByEmailIgnoreCaseOrderByOrderDateDesc(buyer.getEmail()).size());
  assertEquals(1,offers.findById(offer.getId()).orElseThrow().getUsedCount());
  assertEquals(3,products.findById(p.getProductId()).orElseThrow().getQuantity());
 }
}
