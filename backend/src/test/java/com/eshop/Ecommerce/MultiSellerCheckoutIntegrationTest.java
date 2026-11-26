package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiSellerCheckoutIntegrationTest {
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired CartRepository carts;
 @Autowired CartItemRepository cartItems;
 @Autowired AddressRepository addresses;
 @Autowired OrderService orderService;
 @Autowired OrderItemRepo orderItems;

 @Test
 void purchasingSecondSellerOfferOnlyReservesThatSellerInventory() {
  String tag=UUID.randomUUID().toString().substring(0,8);
  User sellerA=users.save(new User("sa"+tag,"sa"+tag+"@example.org","test-hash"));
  User sellerB=users.save(new User("sb"+tag,"sb"+tag+"@example.org","test-hash"));
  User buyer=users.save(new User("cb"+tag,"cb"+tag+"@example.org","test-hash"));
  Category category=new Category();category.setCategoryName("Matched checkout "+tag);
  category=categories.save(category);
  Product canonical=new Product();canonical.setProductName("Wireless headset "+tag);
  canonical.setDescription("A canonical product shared across sellers");
  canonical.setCategory(category);canonical.setUser(sellerA);
  canonical.setQuantity(25);canonical.setPrice(1499);canonical.setSpecialPrice(1499);
  canonical=products.saveAndFlush(canonical);
  Product second=new Product();second.setProductName(canonical.getProductName());
  second.setDescription(canonical.getDescription());second.setCategory(category);
  second.setCatalogProductId(canonical.getProductId());second.setUser(sellerB);
  second.setQuantity(10);second.setPrice(1399);second.setSpecialPrice(1399);
  second.setListingStatus("APPROVED");second=products.saveAndFlush(second);
  final Long selectedProductId=second.getProductId();
  Address address=new Address("Test Road","Building B","Bengaluru","Karnataka","India","560001");
  address.setUser(buyer);address=addresses.save(address);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(2798.0);cart=carts.save(cart);
  CartItem item=new CartItem();item.setCart(cart);item.setProduct(second);
  item.setQuantity(2);item.setProductPrice(1399.0);item.setDiscount(0.0);cartItems.saveAndFlush(item);
  var placed=orderService.placeOrder(buyer.getEmail(),address.getAddressId(),
      "COD","COD",null,"PENDING","Pay on delivery");
  assertNotNull(placed.getOrderId());
  assertEquals(25,products.findById(canonical.getProductId()).orElseThrow().getQuantity());
  assertEquals(8,products.findById(second.getProductId()).orElseThrow().getQuantity());
  var sellerBItems=orderItems.findSellerOrderLines(sellerB.getUserid(),PageRequest.of(0,20));
  assertTrue(sellerBItems.stream().anyMatch(line->
      placed.getOrderId().equals(line.getOrder().getOrderId()) &&
      selectedProductId.equals(line.getProduct().getProductId()) &&
      line.getQuantity()==2 && Math.abs(line.getOrderedProductPrice()-1399.0)<0.001));
  var sellerAItems=orderItems.findSellerOrderLines(sellerA.getUserid(),PageRequest.of(0,20));
  assertFalse(sellerAItems.stream().anyMatch(line->
      placed.getOrderId().equals(line.getOrder().getOrderId())));
 }
}
