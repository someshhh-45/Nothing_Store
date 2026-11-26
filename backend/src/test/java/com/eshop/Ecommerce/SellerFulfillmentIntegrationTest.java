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
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SellerFulfillmentIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired ProductRepo products;
 @Autowired CategoryRepo categories;
 @Autowired OrderRepo orders;
 @Autowired OrderItemRepo items;
 @Autowired SellerOrderFulfillmentRepository fulfillments;

 private User makeUser(){
  String id=UUID.randomUUID().toString().substring(0,8);
  return users.save(new User("sf"+id,"sf"+id+"@example.org","test-hash"));
 }
 @Test
 void mixedSellerOrderCannotBeFulfilledByAnotherSeller() throws Exception {
  User a=makeUser(),b=makeUser(),other=makeUser(),buyer=makeUser();
  Category category=new Category();category.setCategoryName("Fulfillment "+UUID.randomUUID().toString().substring(0,8));
  category=categories.save(category);
  Product first=new Product();first.setProductName("Seller A Test Item");
  first.setDescription("Seller A's inventory item for testing");
  first.setQuantity(10);first.setPrice(100);first.setSpecialPrice(100);first.setCategory(category);first.setUser(a);
  first=products.saveAndFlush(first);
  Product second=new Product();second.setProductName("Seller B Test Item");
  second.setDescription("Seller B's inventory item for testing");
  second.setQuantity(10);second.setPrice(200);second.setSpecialPrice(200);second.setCategory(category);second.setUser(b);
  second=products.saveAndFlush(second);
  Order order=new Order();order.setEmail(buyer.getEmail());order.setOrderDate(LocalDate.now());
  order.setOrderStatus("Accepted");order.setTotalAmount(300.0);order=orders.saveAndFlush(order);
  OrderItem lineA=new OrderItem();lineA.setOrder(order);lineA.setProduct(first);lineA.setQuantity(1);lineA.setOrderedProductPrice(100);
  OrderItem lineB=new OrderItem();lineB.setOrder(order);lineB.setProduct(second);lineB.setQuantity(1);lineB.setOrderedProductPrice(200);
  items.saveAllAndFlush(List.of(lineA,lineB));
  Long id=order.getOrderId();

  mvc.perform(get("/api/seller/orders/"+id+"/fulfillment").with(user(a.getUserName()).roles("SELLER")))
     .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("PLACED"));
  mvc.perform(put("/api/seller/orders/"+id+"/fulfillment")
     .with(user(other.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"CONFIRMED\"}"))
     .andExpect(status().isNotFound());
  mvc.perform(put("/api/seller/orders/"+id+"/fulfillment")
     .with(user(a.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SHIPPED\"}"))
     .andExpect(status().isConflict());
  mvc.perform(put("/api/seller/orders/"+id+"/fulfillment")
     .with(user(a.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"CONFIRMED\"}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("CONFIRMED"));
  mvc.perform(put("/api/seller/orders/"+id+"/fulfillment")
     .with(user(a.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"PACKED\"}"))
     .andExpect(status().isOk());
  mvc.perform(put("/api/seller/orders/"+id+"/fulfillment")
     .with(user(a.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON)
     .content("{\"stage\":\"SHIPPED\",\"carrier\":\"DemoCourier\",\"trackingNumber\":\"TEST12345\"}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.trackingNumber").value("TEST12345"));
  mvc.perform(get("/api/seller/orders/"+id+"/fulfillment").with(user(b.getUserName()).roles("SELLER")))
     .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("PLACED"));
  assertEquals(1,fulfillments.findByOrderId(id).size());
  mvc.perform(get("/api/customer/orders/"+id+"/seller-fulfillments")
     .with(user(other.getUserName()).roles("USER")))
     .andExpect(status().isNotFound());
  var result=mvc.perform(get("/api/customer/orders/"+id+"/seller-fulfillments")
     .with(user(buyer.getUserName()).roles("USER")))
     .andExpect(status().isOk()).andReturn();
  var nodes=json.readTree(result.getResponse().getContentAsString());
  assertEquals(2,nodes.size());
  assertEquals(Set.of("SHIPPED","PLACED"),Set.of(nodes.get(0).path("stage").asText(),nodes.get(1).path("stage").asText()));
 }
}
