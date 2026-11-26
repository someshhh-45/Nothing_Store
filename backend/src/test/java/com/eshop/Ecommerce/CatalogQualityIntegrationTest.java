package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.extension.CatalogQualityController;
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
class CatalogQualityIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired ProductRepo products;
 @Autowired CategoryRepo categories;
 @Autowired CatalogQualityReviewRepository reviews;

 private Product product(Category cat,User seller,String title,String photo){
  Product p=new Product();
  p.setProductName(title);p.setBrand("Example Brand");
  p.setDescription("This is an example product for a catalog audit");
  p.setCategory(cat);p.setUser(seller);p.setQuantity(10);
  p.setCity("Bengaluru");p.setPrice(499);p.setSpecialPrice(499);
  p.setImage(photo);
  return products.saveAndFlush(p);
 }
 @Test
 void adminCanReviewNonDestructivelyAndSellerOffersAreNotFalseDuplicates()throws Exception {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User a=users.save(new User("qaa"+suffix,"qaa"+suffix+"@example.org","test-hash"));
  User b=users.save(new User("qab"+suffix,"qab"+suffix+"@example.org","test-hash"));
  Category cat=new Category();cat.setCategoryName("Audit "+suffix);cat=categories.save(cat);
  String title="Test Product "+suffix;
  String photo="https://images.example.org/audit-"+suffix+".jpg";
  Product first=product(cat,a,title,photo);
  Product distinct=product(cat,b,title,photo);
  Product linked=product(cat,b,title,photo);linked.setCatalogProductId(first.getProductId());
  products.saveAndFlush(linked);
  Product missing=product(cat,a,"Missing Photo "+suffix,null);

  mvc.perform(get("/api/admin/catalog-quality")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/catalog-quality")
    .with(user(a.getUserName()).roles("SELLER"))).andExpect(status().isForbidden());

  var res=mvc.perform(get("/api/admin/catalog-quality")
    .with(user(a.getUserName()).roles("ADMIN")))
    .andExpect(status().isOk()).andReturn();
  var issues=json.readTree(res.getResponse().getContentAsString()).path("issues");
  var candidates=java.util.stream.StreamSupport.stream(issues.spliterator(),false).toList();
  var duplicate=candidates.stream().filter(i->i.path("type").asText().equals("POSSIBLE_DUPLICATE"))
   .filter(i->i.path("listings").toString().contains(title)).findFirst().orElseThrow();
  assertEquals(2,duplicate.path("listings").size(),"Linked seller offer is not an independent duplicate");
  String key=duplicate.path("key").asText();
  assertTrue(key.startsWith("possible_duplicate-"));
  var shared=candidates.stream().filter(i->i.path("type").asText().equals("SHARED_IMAGE"))
   .filter(i->i.path("listings").toString().contains(title)).findFirst().orElseThrow();
  assertEquals(2,shared.path("listings").size());
  assertTrue(candidates.stream().anyMatch(i->i.path("type").asText().equals("MISSING_IMAGE")&&
    i.path("listings").toString().contains("Missing Photo "+suffix)));

  mvc.perform(put("/api/admin/catalog-quality/"+key+"/review")
    .with(user(a.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"status\":\"DISMISSED\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/catalog-quality/"+key+"/review")
    .with(user(a.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"status\":\"REVIEWED\",\"note\":\"Checked with sellers\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
  assertEquals("REVIEWED",reviews.findById(key).orElseThrow().getStatus());
  assertEquals(10,products.findById(first.getProductId()).orElseThrow().getQuantity());
  assertEquals(10,products.findById(distinct.getProductId()).orElseThrow().getQuantity());
  assertEquals(first.getProductId(),products.findById(linked.getProductId()).orElseThrow().getCatalogProductId());

  mvc.perform(put("/api/admin/catalog-quality/"+key+"/review")
    .with(user(a.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"status\":\"OPEN\"}"))
    .andExpect(status().isOk());
  assertFalse(reviews.existsById(key));
  mvc.perform(put("/api/admin/catalog-quality/possible_duplicate-00000000000000000000000000000000/review")
    .with(user(a.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"status\":\"REVIEWED\"}"))
    .andExpect(status().isNotFound());
 }
}
