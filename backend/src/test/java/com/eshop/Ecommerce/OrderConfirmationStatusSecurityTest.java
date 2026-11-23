package com.eshop.Ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderConfirmationStatusSecurityTest {
 @Autowired MockMvc mvc;
 @Test void buyerConfirmationStatusRequiresAuthentication()throws Exception{
  mvc.perform(get("/api/customer/orders/42/confirmation")).andExpect(status().isUnauthorized());
 }
 @Test void adminRetryRequiresAuthentication()throws Exception{
  mvc.perform(post("/api/admin/orders/42/confirmation/retry")).andExpect(status().isUnauthorized());
 }
 @WithMockUser(roles="USER")
 @Test void regularCustomerCannotResetEmailDelivery()throws Exception{
  mvc.perform(post("/api/admin/orders/42/confirmation/retry")).andExpect(status().isForbidden());
 }
}
