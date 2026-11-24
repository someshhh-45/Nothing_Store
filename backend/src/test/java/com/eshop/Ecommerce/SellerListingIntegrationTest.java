package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties="nothing.upload.dir=/tmp/nothing-seller-upload-tests")
class SellerListingIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired ProductRepo products;
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;

 private String unique(){return UUID.randomUUID().toString().substring(0,8);}

 @Test
 void anonymousAndCustomersCannotCreateSellerProducts() throws Exception {
  mvc.perform(post("/api/seller/listings").contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/seller/listings").with(user("customer").roles("USER"))
    .contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isForbidden());
 }

 @Test
 void sellerCanCreateAndUploadImageButOtherSellerCannotReplaceIt() throws Exception {
  String name="sl"+unique();
  User seller=users.save(new User(name,name+"@example.org","unused-hash"));
  Category category=new Category();
  category.setCategoryName("Seller Test "+unique());
  category=categories.save(category);
  String payload=json.writeValueAsString(Map.of(
     "categoryId",category.getCategoryId(),"productName","Seller Test Headphones",
     "description","Durable headphones for testing",
     "brand","Test Brand","city","Bengaluru","price",599.0,"quantity",7));
  var result=mvc.perform(post("/api/seller/listings").with(user(name).roles("SELLER"))
       .contentType(MediaType.APPLICATION_JSON).content(payload))
       .andExpect(status().isCreated()).andReturn();
  long productId=json.readTree(result.getResponse().getContentAsString()).path("productId").asLong();
  var stored=products.findById(productId).orElseThrow();
  assertEquals(seller.getUserid(),stored.getUser().getUserid());
  assertEquals(7,stored.getQuantity());
  assertEquals(599.0,stored.getSpecialPrice());

  ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  var picture=new BufferedImage(12,12,BufferedImage.TYPE_INT_RGB);
  assertTrue(ImageIO.write(picture,"png",buffer));
  var upload=new MockMultipartFile("image","product.png","image/png",buffer.toByteArray());
  String otherName="sl"+unique();
  users.save(new User(otherName,otherName+"@example.org","unused-hash"));
  mvc.perform(multipart("/api/seller/listings/"+productId+"/image")
        .file(upload).with(user(otherName).roles("SELLER")))
        .andExpect(status().isForbidden());
  mvc.perform(multipart("/api/seller/listings/"+productId+"/image")
        .file(upload).with(user(name).roles("SELLER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.image").value(org.hamcrest.Matchers.startsWith("/uploads/")));
  assertTrue(products.findById(productId).orElseThrow().getImage().startsWith("/uploads/"));
 }

 @Test
 void sellerPayloadValidationRejectsBadPriceAndStock() throws Exception {
  mvc.perform(post("/api/seller/listings").with(user("seller").roles("SELLER"))
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
      "categoryId",1,"productName","Test product","description","A test description",
      "brand","Test","city","Bengaluru","price",-1,"quantity",-3))))
   .andExpect(status().isBadRequest());
 }
}
