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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SellerApprovalWorkflowTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired RoleRepository roles;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;

 private String unique(){return UUID.randomUUID().toString().substring(0,8);}
 private User makeUser(String prefix,AppRole role){
  String username=prefix+unique();
  User u=new User(username,username+"@example.org","unused-test-hash");
  u.setRoles(Set.of(roles.findByRoleName(role).orElseThrow()));
  return users.save(u);
 }
 @Test
 void applicationRequiresAdminApprovalAndProductReviewBeforePublicSale() throws Exception {
  User applicant=makeUser("sl",AppRole.ROLE_USER);
  User admin=makeUser("sa",AppRole.ROLE_ADMIN);
  var create=mvc.perform(post("/api/seller-applications")
      .with(user(applicant.getUserName()).roles("USER"))
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of(
         "businessName","Example Local Seller","contactPhone","9876543210","gstin",""))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PENDING")).andReturn();
  long profileId=json.readTree(create.getResponse().getContentAsString()).path("id").asLong();

  mvc.perform(get("/api/admin/seller-applications").with(user(applicant.getUserName()).roles("USER")))
     .andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/seller-applications/"+profileId+"/review")
     .with(user(admin.getUserName()).roles("ADMIN"))
     .contentType(MediaType.APPLICATION_JSON)
     .content(json.writeValueAsString(Map.of("decision","APPROVED","note","Test applicant approved"))))
     .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  assertTrue(users.findById(applicant.getUserid()).orElseThrow().getRoles().stream()
      .anyMatch(role->role.getRoleName()==AppRole.ROLE_SELLER));

  Category category=new Category();
  category.setCategoryName("Seller Approval "+unique());
  category=categories.save(category);
  var listing=mvc.perform(post("/api/seller/listings")
       .with(user(applicant.getUserName()).roles("SELLER"))
       .contentType(MediaType.APPLICATION_JSON)
       .content(json.writeValueAsString(Map.of(
          "categoryId",category.getCategoryId(),"productName","Test Marketplace Headphones",
          "description","A listing awaiting manual approval",
          "brand","Example Brand","city","Bengaluru","price",599.0,"quantity",3))))
       .andExpect(status().isCreated())
       .andExpect(jsonPath("$.status").value("PENDING")).andReturn();
  long id=json.readTree(listing.getResponse().getContentAsString()).path("productId").asLong();
  assertEquals("PENDING",products.findById(id).orElseThrow().getListingStatus());
  assertFalse(json.readTree(mvc.perform(get("/api/store/catalog")).andReturn()
      .getResponse().getContentAsString()).findValuesAsText("productId")
      .contains(String.valueOf(id)));

  mvc.perform(put("/api/admin/seller-listings/"+id+"/review")
     .with(user(applicant.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON)
     .content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/seller-listings/"+id+"/review")
     .with(user(admin.getUserName()).roles("ADMIN"))
     .contentType(MediaType.APPLICATION_JSON)
     .content("{\"decision\":\"APPROVED\",\"note\":\"Looks good\"}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  assertEquals("APPROVED",products.findById(id).orElseThrow().getListingStatus());
  var publishedCatalog=json.readTree(mvc.perform(get("/api/store/catalog")).andExpect(status().isOk()).andReturn()
      .getResponse().getContentAsString());
  assertTrue(publishedCatalog.findValuesAsText("productId").contains(String.valueOf(id)));
  var publishedProduct=java.util.stream.StreamSupport.stream(publishedCatalog.spliterator(),false)
      .filter(node->node.path("productId").asLong()==id).findFirst().orElseThrow();
  assertEquals("Example Local Seller",publishedProduct.path("sellerName").asText());
  assertEquals(applicant.getUserid(),publishedProduct.path("sellerId").asLong());

  mvc.perform(put("/api/seller/listings/"+id)
     .with(user(applicant.getUserName()).roles("SELLER"))
     .contentType(MediaType.APPLICATION_JSON)
     .content(json.writeValueAsString(Map.of(
          "categoryId",category.getCategoryId(),"productName","Updated Marketplace Headphones",
          "description","Updated details must be reviewed",
          "brand","Example Brand","city","Bengaluru","price",699.0,"quantity",3))))
     .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  assertEquals("PENDING",products.findById(id).orElseThrow().getListingStatus());
 }
}
