package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CheckoutIntegrationTest {
 @Autowired private UserRepository users;
 @Autowired private CategoryRepo categories;
 @Autowired private ProductRepo products;
 @Autowired private CartRepository carts;
 @Autowired private CartItemRepository cartItems;
 @Autowired private AddressRepository addresses;
 @Autowired private OrderService orders;
 @Autowired private JdbcTemplate jdbc;
 @Autowired private com.eshop.Ecommerce.extension.OrderConfirmationComposer composer;

 @Test
 void cashOnDeliveryReservesStockAndWritesEvents() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User buyer=new User("buyer"+suffix,"buyer"+suffix+"@example.org","unused-test-password-hash");
  buyer=users.save(buyer);
  Category category=new Category();category.setCategoryName("Testing "+suffix);
  category=categories.save(category);
  Product product=new Product();
  product.setProductName("Integration Coffee "+suffix);
  product.setDescription("A test product used for checkout verification");
  product.setQuantity(1);product.setPrice(149);product.setSpecialPrice(149);
  product.setCategory(category);product=products.save(product);
  Address address=new Address("Test Main Road","Building Alpha","Bengaluru","Karnataka","India","560001");
  address.setUser(buyer);address=addresses.save(address);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(149.0);
  cart=carts.save(cart);
  CartItem item=new CartItem();
  item.setCart(cart);item.setProduct(product);item.setQuantity(1);
  item.setDiscount(0.0);item.setProductPrice(149.0);
  cartItems.save(item);
  var placed=orders.placeOrder(buyer.getEmail(),address.getAddressId(),"COD","COD",null,"PENDING","Cash on delivery");
  assertNotNull(placed.getOrderId());
  assertEquals(0,products.findById(product.getProductId()).orElseThrow().getQuantity());
  Long events=jdbc.queryForObject("select count(*) from nothing_outbox where payload->>'type'='ORDER_PLACED' and (payload->>'orderId')::bigint=?",Long.class,placed.getOrderId());
  assertEquals(1L,events);
  // One durable email job is committed with COD order; SMTP is NOT contacted
  // when notifications are disabled (the default in CI).
  Long emailJobs=jdbc.queryForObject(
    "select count(*) from order_confirmation_emails where order_id=? and status='PENDING'",
    Long.class,placed.getOrderId());
  assertEquals(1L,emailJobs);
  var receipt=composer.compose(placed.getOrderId());
  assertEquals(buyer.getEmail(),receipt.to());
  assertTrue(receipt.subject().contains(placed.getOrderId().toString()));
  assertTrue(receipt.plainText().contains("Cash on delivery"));
  assertTrue(receipt.plainText().contains("Integration Coffee"));
  assertTrue(receipt.plainText().contains("INR 149.00"));
  assertTrue(receipt.htmlText().contains("Bengaluru"));
 }
}