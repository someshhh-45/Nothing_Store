package com.eshop.Ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class StripeCheckoutSecurityTest {
 @Autowired MockMvc mvc;

 @Test
 void checkoutSessionRequiresLogin()throws Exception{
  mvc.perform(post("/api/checkout/stripe/session")
   .contentType(MediaType.APPLICATION_JSON)
   .content("{\"addressId\":1}")).andExpect(status().isUnauthorized());
 }

 @Test
 void unsignedWebhookIsRejected()throws Exception{
  mvc.perform(post("/api/stripe/webhook")
   .contentType(MediaType.APPLICATION_JSON)
   .content("{\"type\":\"checkout.session.completed\"}")).andExpect(status().isBadRequest());
 }

 @Test
 void publicClientSuppliedAmountEndpointIsRemoved()throws Exception{
  mvc.perform(post("/api/order/stripe-client-secret")
   .contentType(MediaType.APPLICATION_JSON)
   .content("{\"amount\":100}")).andExpect(status().isUnauthorized());
 }

 @Test
 void configurationReportsStripeDisabledWithoutSecrets()throws Exception{
  mvc.perform(get("/api/checkout/stripe/config"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.enabled").value(false));
 }
}
