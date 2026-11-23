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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AfterSaleApiTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired OrderRepo orders;
 @MockBean AuthUtil auth;

 @Test @WithMockUser(username="after-sale-buyer")
 void deliveryChangesRequireReviewAndReturnsRequireDelivery()throws Exception{
  String tag=UUID.randomUUID().toString().substring(0,8);
  User buyer=users.save(new User("sale"+tag,"sale"+tag+"@example.org","hash"));
  when(auth.loggedInUser()).thenReturn(buyer);
  when(auth.loggedInUserId()).thenReturn(buyer.getUserid());
  when(auth.loggedInEmail()).thenReturn(buyer.getEmail());
  Order order=new Order();order.setEmail(buyer.getEmail());order.setOrderStatus("Accepted");
  order.setOrderDate(LocalDate.now());order=orders.save(order);
  Long id=order.getOrderId();
  String date=LocalDate.now().plusDays(4).toString();

  mvc.perform(post("/api/customer/orders/"+id+"/requests")
    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
     Map.of("type","RETURN","reason","I want to return this"))))
    .andExpect(status().isConflict());
  mvc.perform(post("/api/customer/orders/"+id+"/requests")
    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
     Map.of("type","RESCHEDULE","reason","Nobody home on first date","requestedDate",date))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  assertNull(orders.findById(id).orElseThrow().getEstimatedDeliveryDate(),
   "Customer requests must not unilaterally change fulfillment dates");
  mvc.perform(get("/api/customer/order-requests"))
    .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("RESCHEDULE"));
  mvc.perform(get("/api/admin/order-requests")).andExpect(status().isForbidden());
 }
}
