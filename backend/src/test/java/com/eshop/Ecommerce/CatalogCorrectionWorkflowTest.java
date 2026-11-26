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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogCorrectionWorkflowTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired ProductRepo products;
 @Autowired CategoryRepo categories;
 @Autowired CatalogCorrectionRequestRepository corrections;

 private User member(String prefix){
  String id=UUID.randomUUID().toString().substring(0,8);
  return users.save(new User(prefix+id,prefix+id+"@example.org","test-password-hash"));
 }
 private Product listing(Category c,User seller,String name,int stock){
  Product p=new Product();p.setProductName(name);p.setDescription("A valid catalog description for testing");
  p.setBrand("Demo Brand");p.setCategory(c);p.setCity("Bengaluru");
  p.setPrice(999);p.setSpecialPrice(999);p.setQuantity(stock);p.setUser(seller);
  return products.saveAndFlush(p);
 }
 @Test
 void sellerCorrectionRequiresRealPendingChangeAndAdminApproval() throws Exception {
  User seller=member("cor"),intruder=member("int"),admin=member("adm");
  Category category=new Category();category.setCategoryName("Correction "+UUID.randomUUID().toString().substring(0,8));
  category=categories.save(category);
  Product target=listing(category,seller,"Wireless Charger",12);
  Long id=target.getProductId();
  var created=mvc.perform(post("/api/admin/catalog-corrections")
    .with(user(admin.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(Map.of("productId",id,"kind","DETAILS","message","Correct the technical description"))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn();
  long correctionId=json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
  mvc.perform(post("/api/admin/catalog-corrections")
    .with(user(admin.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(Map.of("productId",id,"kind","DETAILS","message","Duplicate request"))))
    .andExpect(status().isConflict());
  mvc.perform(get("/api/seller/catalog-corrections")
    .with(user(intruder.getUserName()).roles("SELLER")))
    .andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(post("/api/seller/catalog-corrections/"+correctionId+"/submit")
    .with(user(intruder.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isNotFound());
  mvc.perform(post("/api/seller/catalog-corrections/"+correctionId+"/submit")
    .with(user(seller.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isConflict());
  mvc.perform(put("/api/seller/listings/"+id)
    .with(user(seller.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(Map.of("categoryId",category.getCategoryId(),
     "productName","Wireless Charger","description","Improved USB C charging description",
     "brand","Demo Brand","city","Bengaluru","price",999.0,"quantity",12))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  mvc.perform(post("/api/seller/catalog-corrections/"+correctionId+"/submit")
    .with(user(seller.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Corrected description\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
  mvc.perform(put("/api/admin/catalog-corrections/"+correctionId+"/review")
    .with(user(intruder.getUserName()).roles("SELLER"))
    .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
    .andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/catalog-corrections/"+correctionId+"/review")
    .with(user(admin.getUserName()).roles("ADMIN"))
    .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  Product updated=products.findById(id).orElseThrow();
  assertEquals(12,updated.getQuantity());
  assertEquals("Improved USB C charging description",updated.getDescription());
  assertEquals("APPROVED",updated.getListingStatus());
  assertEquals("APPROVED",corrections.findById(correctionId).orElseThrow().getStatus());
 }
 @Test
 void linkPreservesIdsOwnershipAndStockAndRejectsDifferentModels()throws Exception{
  User first=member("ca"),second=member("cb"),admin=member("cc");
  Category category=new Category();category.setCategoryName("Catalog links "+UUID.randomUUID().toString().substring(0,8));
  category=categories.save(category);
  Product canonical=listing(category,first,"Wireless Speaker",25);
  Product sellerOffer=listing(category,second,"Wireless Speaker",10);
  Long canonicalId=canonical.getProductId(),offerId=sellerOffer.getProductId();
  mvc.perform(post("/api/admin/catalog-quality/link")
   .with(user(first.getUserName()).roles("SELLER"))
   .contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("productId",offerId,"canonicalProductId",canonicalId,
    "reason","Identical model number"))))
   .andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/catalog-quality/link")
   .with(user(admin.getUserName()).roles("ADMIN"))
   .contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("productId",offerId,"canonicalProductId",canonicalId,
    "reason","Identical model number"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.linked").value(true));
  assertEquals(canonicalId,products.findById(offerId).orElseThrow().getCatalogProductId());
  assertEquals(25,products.findById(canonicalId).orElseThrow().getQuantity());
  assertEquals(10,products.findById(offerId).orElseThrow().getQuantity());
  assertEquals(second.getUserid(),products.findById(offerId).orElseThrow().getUser().getUserid());
  mvc.perform(post("/api/admin/catalog-quality/link")
   .with(user(admin.getUserName()).roles("ADMIN"))
   .contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("productId",offerId,"canonicalProductId",canonicalId,
    "reason","Repeat attempted link"))))
   .andExpect(status().isConflict());
 }
}
