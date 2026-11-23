package com.eshop.Ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutAuthenticationTest {
 @Autowired MockMvc mvc;

 @Test
 void anonymousIdentityIsNeverPresentedAsSignedIn() throws Exception {
  mvc.perform(get("/api/auth/username")).andExpect(status().isOk())
    .andExpect(content().string(""));
 }

 @Test
 void protectedCartRequiresAuthentication() throws Exception {
  mvc.perform(get("/api/carts")).andExpect(status().isUnauthorized());
 }

 @Test
 void userDetailsRejectAnonymousUsers() throws Exception {
  mvc.perform(get("/api/auth/user")).andExpect(status().isUnauthorized());
 }

 @Test
 @WithMockUser(username="checkout-tester")
 void authenticatedIdentityIsReturned() throws Exception {
  mvc.perform(get("/api/auth/username")).andExpect(status().isOk())
    .andExpect(content().string("checkout-tester"));
 }
}
