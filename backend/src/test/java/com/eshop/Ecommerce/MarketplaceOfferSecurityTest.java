package com.eshop.Ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceOfferSecurityTest {
 @Autowired MockMvc mvc;
 @Test void publicOffersAreVisible()throws Exception {
  mvc.perform(get("/api/store/offers")).andExpect(status().isOk());
 }
 @Test void applyingCouponRequiresLogin()throws Exception {
  mvc.perform(put("/api/customer/cart/coupon").contentType(MediaType.APPLICATION_JSON)
    .content("{\"code\":\"HELLO10\"}")).andExpect(status().isUnauthorized());
 }
 @Test void campaignCreationRequiresAdmin()throws Exception {
  mvc.perform(post("/api/admin/offers").contentType(MediaType.APPLICATION_JSON)
    .content("{}")).andExpect(status().isUnauthorized());
 }
 @Test @WithMockUser(roles="USER")
 void regularCustomerCannotAccessAdminCampaignManagement()throws Exception {
  mvc.perform(get("/api/admin/offers")).andExpect(status().isForbidden());
 }
}
