package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.OrderService;
import com.eshop.Ecommerce.Service.DeliveryCoverageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryCoverageIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired CartRepository carts;
 @Autowired CartItemRepository items;
 @Autowired AddressRepository addresses;
 @Autowired DeliveryCoverageZoneRepository zones;
 @Autowired DeliveryCoverageService coverage;
 @Autowired OrderService orders;

 private User newUser(String prefix){
  String id=UUID.randomUUID().toString().substring(0,8);
  return users.save(new User(prefix+id,prefix+id+"@example.org","test-hash"));
 }
 private Product product(Category c,User seller){
  Product p=new Product();
  p.setProductName("Delivery Sample Product");
  p.setDescription("A sample product with delivery eligibility");
  p.setCategory(c);p.setUser(seller);p.setQuantity(5);
  p.setPrice(399);p.setSpecialPrice(399);p.setCity("Chennai");
  return products.saveAndFlush(p);
 }
 @Test
 void sellerPinRangesAreIsolatedAndCODCannotBypassCoverage() throws Exception {
  User seller=newUser("ds"),other=newUser("do"),buyer=newUser("db");
  Category cat=new Category();cat.setCategoryName("Delivery "+UUID.randomUUID().toString().substring(0,8));cat=categories.save(cat);
  Product p=product(cat,seller);
  String sellerPath="/api/seller/delivery/zones";
  String input="{\"pinFrom\":\"560010\",\"pinTo\":\"560020\",\"etaMinDays\":2,\"etaMaxDays\":5}";
  var saved=mvc.perform(post(sellerPath).with(user(seller.getUserName()).roles("SELLER"))
   .contentType(MediaType.APPLICATION_JSON).content(input))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.pinFrom").value("560010"))
   .andReturn();
  Long zoneId=json.readTree(saved.getResponse().getContentAsString()).path("id").asLong();
  mvc.perform(post(sellerPath).with(user(seller.getUserName()).roles("SELLER"))
   .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isConflict());
  mvc.perform(post(sellerPath).with(user(seller.getUserName()).roles("SELLER"))
   .contentType(MediaType.APPLICATION_JSON)
   .content("{\"pinFrom\":\"560005\",\"pinTo\":\"560025\",\"etaMinDays\":2,\"etaMaxDays\":4}"))
   .andExpect(status().isConflict());
  mvc.perform(get(sellerPath).with(user(other.getUserName()).roles("SELLER")))
   .andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(delete(sellerPath+"/"+zoneId).with(user(other.getUserName()).roles("SELLER")))
   .andExpect(status().isNotFound());

  mvc.perform(get("/api/store/delivery/check").param("productId",p.getProductId().toString())
    .param("pincode","560015")).andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("SERVICEABLE"))
    .andExpect(jsonPath("$.maxDays").value(5));
  mvc.perform(get("/api/store/delivery/check").param("productId",p.getProductId().toString())
    .param("pincode","600001")).andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
    .andExpect(jsonPath("$.checkoutAllowed").value(false));
  mvc.perform(get("/api/store/delivery/check").param("productId",p.getProductId().toString())
    .param("pincode","invalid")).andExpect(status().isBadRequest());

  Address address=new Address("Main Street","Number Five","Bengaluru","Karnataka","India","600001");
  address.setUser(buyer);address=addresses.save(address);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(399.0);cart=carts.save(cart);
  CartItem item=new CartItem();item.setCart(cart);item.setProduct(p);item.setQuantity(1);
  item.setProductPrice(399.0);item.setDiscount(0.0);items.saveAndFlush(item);
  mvc.perform(get("/api/customer/delivery/cart").param("addressId",address.getAddressId().toString())
    .with(user(buyer.getUserName()).roles("USER")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutAllowed").value(false));
  mvc.perform(get("/api/customer/delivery/cart").param("addressId",address.getAddressId().toString())
    .with(user(other.getUserName()).roles("USER"))).andExpect(status().isNotFound());

  final Long deliveryAddressId=address.getAddressId();
  assertThrows(RuntimeException.class,()->orders.placeOrder(buyer.getEmail(),deliveryAddressId,
    "COD","COD",null,"PENDING","Pay at delivery"));
  assertEquals(5,products.findById(p.getProductId()).orElseThrow().getQuantity());
  assertEquals(1,items.findAll().stream().filter(i->i.getProduct().getProductId().equals(p.getProductId())).count());

  address.setPincode("560015");addresses.saveAndFlush(address);
  mvc.perform(get("/api/customer/delivery/cart").param("addressId",deliveryAddressId.toString())
    .with(user(buyer.getUserName()).roles("USER")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutAllowed").value(true));

  mvc.perform(delete(sellerPath+"/"+zoneId).with(user(seller.getUserName()).roles("SELLER")))
   .andExpect(status().isOk());
  assertEquals("UNAVAILABLE",coverage.check(p,"560015").status(),
    "Disabling every zone must not turn it back into unverified/implicitly deliverable");
 }
}
