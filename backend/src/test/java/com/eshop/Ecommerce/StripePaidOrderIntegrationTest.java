package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No external Stripe account is needed: the webhook is Stripe-format and
 * cryptographically signed using a test-only secret.
 * Verifies the database, inventory, outbox and webhook replay behavior.
 */
@SpringBootTest(properties="stripe.webhook.secret=whsec_offline_integration_test")
@AutoConfigureMockMvc
class StripePaidOrderIntegrationTest {
 private static final String WEBHOOK_SECRET="whsec_offline_integration_test";
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired CartRepository carts;
 @Autowired CartItemRepository cartItems;
 @Autowired AddressRepository addresses;
 @Autowired StripeCheckoutAttemptRepository attempts;
 @Autowired OrderRepo orders;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

 private String sign(String body)throws Exception {
  long timestamp=Instant.now().getEpochSecond();
  Mac mac=Mac.getInstance("HmacSHA256");
  mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  String hashed=HexFormat.of().formatHex(mac.doFinal(
    (timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
  return "t="+timestamp+",v1="+hashed;
 }

 @Test
 void signedPaidWebhookCreatesOneOrderAndReplayDoesNotDuplicate()throws Exception {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User buyer=users.save(new User("stripe"+suffix,"stripe"+suffix+"@example.org","unused-test-password"));
  Category category=new Category();category.setCategoryName("Stripe Integration "+suffix);
  category=categories.save(category);
  Product product=new Product();
  product.setProductName("Webhook Test Product "+suffix);
  product.setDescription("A product used to test verified checkout webhook events");
  product.setQuantity(5);product.setPrice(275);product.setSpecialPrice(275);
  product.setCategory(category);product=products.saveAndFlush(product);
  Address address=new Address("Demo Main Road","Apartment Eight","Bengaluru",
    "Karnataka","India","560001");
  address.setUser(buyer);address=addresses.save(address);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(550.0);
  cart=carts.save(cart);
  CartItem item=new CartItem();item.setCart(cart);item.setProduct(product);
  item.setQuantity(2);item.setProductPrice(275.0);item.setDiscount(0.0);
  cartItems.saveAndFlush(item);

  StripeCheckoutAttempt attempt=new StripeCheckoutAttempt();
  attempt.setId(UUID.randomUUID().toString());
  attempt.setSessionId("cs_test_"+suffix);
  attempt.setEmail(buyer.getEmail());
  attempt.setAddressId(address.getAddressId());
  attempt.setExpectedAmount(55000);
  attempt.setCartFingerprint(product.getProductId()+":2:27500;");
  attempts.saveAndFlush(attempt);

  String event=mapper.writeValueAsString(Map.of(
    "id","evt_"+suffix,"object","event","type","checkout.session.completed",
    "data",Map.of("object",Map.of(
      "id",attempt.getSessionId(),"object","checkout.session","payment_status","paid",
      "currency","inr","amount_total",55000,
      "metadata",Map.of("checkoutAttemptId",attempt.getId())))));
  String signature=sign(event);
  mvc.perform(post("/api/stripe/webhook")
      .contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature",signature)
      .content(event)).andExpect(status().isOk());
  var saved=attempts.findById(attempt.getId()).orElseThrow();
  assertEquals("COMPLETED",saved.getStatus());
  assertNotNull(saved.getOrderId());
  assertEquals(3,products.findById(product.getProductId()).orElseThrow().getQuantity());
  assertEquals(1,orders.findByEmailIgnoreCaseOrderByOrderDateDesc(buyer.getEmail()).size());

  // Stripe retries events. Our verified callback must be idempotent.
  mvc.perform(post("/api/stripe/webhook")
      .contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature",signature)
      .content(event)).andExpect(status().isOk());
  assertEquals(1,orders.findByEmailIgnoreCaseOrderByOrderDateDesc(buyer.getEmail()).size());
  assertEquals(3,products.findById(product.getProductId()).orElseThrow().getQuantity());
  Long outbox=jdbc.queryForObject("select count(*) from nothing_outbox where payload->>'type'='ORDER_PLACED' and (payload->>'orderId')::bigint=?",Long.class,saved.getOrderId());
  assertEquals(1L,outbox);
  Long emailJobs=jdbc.queryForObject(
    "select count(*) from order_confirmation_emails where order_id=?",
    Long.class,saved.getOrderId());
  assertEquals(1L,emailJobs,"Signed paid checkout creates exactly one durable confirmation job");
 }
}
