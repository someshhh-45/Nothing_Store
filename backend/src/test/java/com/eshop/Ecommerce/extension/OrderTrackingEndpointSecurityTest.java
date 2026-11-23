package com.eshop.Ecommerce.extension;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderTrackingEndpointSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousCannotReadOrWriteAnyOrders() throws Exception {
        mvc.perform(get("/api/customer/orders/9/tracking")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders/9/tracking")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/orders/9/tracking")
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SHIPPED\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles="USER")
    void ordinaryCustomersCannotMarkOrdersShipped() throws Exception {
        mvc.perform(put("/api/admin/orders/9/tracking")
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SHIPPED\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/seller/orders/9/tracking")
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SHIPPED\"}"))
            .andExpect(status().isForbidden());
    }
}
