package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Util.AuthUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MarketplaceEngagementApiTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired OrderRepo orders;
 @Autowired OrderItemRepo items;
 @MockBean AuthUtil auth;

 @Test @WithMockUser(username="engagement-buyer")
 void wishlistPersistsAndReviewsRequireARealPurchase()throws Exception{
  String tag=UUID.randomUUID().toString().substring(0,8);
  User buyer=users.save(new User("eng"+tag,"eng"+tag+"@example.org","test-hash"));
  when(auth.loggedInUserId()).thenReturn(buyer.getUserid());
  when(auth.loggedInUser()).thenReturn(buyer);
  Category category=new Category();category.setCategoryName("Engagement "+tag);category=categories.save(category);
  Product p=new Product();p.setProductName("Wireless Test "+tag);
  p.setDescription("A test product with a persisted wish and review");
  p.setCategory(category);p.setQuantity(9);p.setPrice(300);p.setSpecialPrice(300);
  p=products.save(p);
  Long id=p.getProductId();

  mvc.perform(put("/api/customer/wishlist/"+id)).andExpect(status().isOk())
   .andExpect(jsonPath("$.saved").value(true));
  mvc.perform(get("/api/customer/wishlist")).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].productId").value(id));

  String feedback=json.writeValueAsString(Map.of("rating",5,"comment","Works well for testing"));
  mvc.perform(post("/api/customer/products/"+id+"/reviews")
    .contentType(MediaType.APPLICATION_JSON).content(feedback))
   .andExpect(status().isForbidden());

  Order order=new Order();order.setEmail(buyer.getEmail());
  order.setOrderStatus("PLACED");order.setOrderDate(LocalDate.now());order=orders.save(order);
  OrderItem line=new OrderItem();line.setOrder(order);line.setProduct(p);
  line.setQuantity(1);line.setOrderedProductPrice(300);items.save(line);
  mvc.perform(post("/api/customer/products/"+id+"/reviews")
   .contentType(MediaType.APPLICATION_JSON).content(feedback))
   .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(5));
  mvc.perform(get("/api/store/products/"+id+"/reviews"))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].author").value(buyer.getUserName()));
 }
}
