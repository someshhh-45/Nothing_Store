package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerBrowserCheckoutTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired RoleRepository roles;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired PasswordEncoder encoder;

 @Test
 void authenticatedCookieIsAcceptedThroughoutRealCodCheckout()throws Exception {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  String username="web"+suffix;
  String password="UserPassword!2026";
  User user=new User(username,username+"@example.org",encoder.encode(password));
  Role userRole=roles.findByRoleName(AppRole.ROLE_USER).orElseThrow();
  user.setRoles(Set.of(userRole));
  users.save(user);
  Category category=new Category();category.setCategoryName("Web checkout "+suffix);
  category=categories.save(category);
  Product product=new Product();
  product.setProductName("Checkout item "+suffix);
  product.setDescription("A product for authenticated browser checkout");
  product.setPrice(250.0);product.setSpecialPrice(250.0);
  product.setQuantity(12);product.setCategory(category);
  product=products.saveAndFlush(product);

  String credentials=json.writeValueAsString(Map.of("username",username,"password",password));
  var login=mvc.perform(post("/api/auth/signin")
   .contentType(MediaType.APPLICATION_JSON).content(credentials))
   .andExpect(status().isOk()).andReturn();
  Cookie jwt=login.getResponse().getCookie("nothingSession");
  assertNotNull(jwt,"Sign-in must issue the authentication cookie");
  assertEquals("/api",jwt.getPath());

  mvc.perform(get("/api/auth/user").cookie(jwt))
   .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));

  String cart=json.writeValueAsString(List.of(Map.of("productId",product.getProductId(),"quantity",2)));
  mvc.perform(post("/api/cart/create").cookie(jwt)
   .contentType(MediaType.APPLICATION_JSON).content(cart))
   .andExpect(status().isCreated());

  String address=json.writeValueAsString(Map.of(
   "street","Main Test Road","buildingName","Building Alpha",
   "city","Bengaluru","state","Karnataka",
   "country","India","pincode","560001"));
  var savedAddress=mvc.perform(post("/api/addresses").cookie(jwt)
   .contentType(MediaType.APPLICATION_JSON).content(address))
   .andExpect(status().isCreated()).andReturn();
  long addressId=json.readTree(savedAddress.getResponse().getContentAsString()).path("addressId").asLong();

  mvc.perform(get("/api/users/addresses").cookie(jwt))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].addressId").value(addressId));

  var order=mvc.perform(post("/api/order/users/payments/COD").cookie(jwt)
   .contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("addressId",addressId,
     "pgName","COD","pgStatus","PENDING","pgResponseMessage","Pay on delivery"))))
   .andExpect(status().isCreated()).andReturn();
  long orderId=json.readTree(order.getResponse().getContentAsString()).path("orderId").asLong();
  assertTrue(orderId>0);
  assertEquals(10,products.findById(product.getProductId()).orElseThrow().getQuantity());

  // Regression: the success path must NOT be replaced with "Full authentication
  // is required" when the frontend immediately loads the order list.
  mvc.perform(get("/api/customer/orders").cookie(jwt))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$[0].orderId").value(orderId))
   .andExpect(jsonPath("$[0].totalAmount").value(500.0));

  // Never expose order history to an anonymous user.
  mvc.perform(get("/api/customer/orders")).andExpect(status().isUnauthorized());
 }
}
