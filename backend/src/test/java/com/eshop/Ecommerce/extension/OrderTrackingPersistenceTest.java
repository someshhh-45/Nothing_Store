package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Order;
import com.eshop.Ecommerce.Repositories.OrderRepo;
import com.eshop.Ecommerce.Repositories.OrderTrackingEventRepository;
import com.eshop.Ecommerce.Util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class OrderTrackingPersistenceTest {
    @Autowired OrderRepo orders;
    @Autowired OrderTrackingEventRepository eventRepo;
    @Autowired OrderTrackingService tracking;
    @MockBean AuthUtil auth;

    private Order sampleOrder() {
        Order order=new Order();
        order.setEmail("tracking-owner@example.test");
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus("Accepted");
        order.setTotalAmount(789.0);
        return orders.saveAndFlush(order);
    }

    @Test
    void milestonesPersistAndNeverClaimLiveCourierGps() {
        Order order=sampleOrder();
        tracking.recordPlaced(order);
        when(auth.loggedInEmail()).thenReturn("tracking-owner@example.test");
        Long id=order.getOrderId();
        assertEquals("PLACED",tracking.customerTracking(id).currentStage());
        assertEquals(1,tracking.customerTracking(id).history().size());
        tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("CONFIRMED","Verified order",null,null,null));
        tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("PACKED","Packed for dispatch",null,null,null));
        tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("SHIPPED","Dispatched","SampleCourier","TST123456",null));
        tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("OUT_FOR_DELIVERY",null,null,null,null));
        tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("DELIVERED",null,null,null,null));
        var state=tracking.customerTracking(id);
        assertEquals("DELIVERED",state.currentStage());
        assertEquals("SampleCourier",state.carrier());
        assertEquals("TST123456",state.trackingNumber());
        assertFalse(state.liveCarrierTracking());
        assertEquals(6,state.history().size());
        assertEquals(6,eventRepo.findByOrderOrderIdOrderByOccurredAtAscIdAsc(id).size());
        assertTrue(state.history().stream().allMatch(e->e.occurredAt()!=null));
        when(auth.loggedInEmail()).thenReturn("not-the-owner@example.test");
        assertThrows(ResponseStatusException.class,()->tracking.customerTracking(id));
    }

    @Test
    void sellerCannotFulfillAnOrderWithoutOwningAnyProduct() {
        Order order=sampleOrder();
        tracking.recordPlaced(order);
        com.eshop.Ecommerce.Model.User seller=new com.eshop.Ecommerce.Model.User();
        seller.setUserid(123456L);
        when(auth.loggedInUser()).thenReturn(seller);
        assertThrows(ResponseStatusException.class,()->
            tracking.updateSeller(order.getOrderId(),
                new OrderTrackingService.UpdateRequest("PACKED",null,null,null,null)));
        assertEquals(1,eventRepo.findByOrderOrderIdOrderByOccurredAtAscIdAsc(order.getOrderId()).size());
    }

    @Test
    void skippingMilestonesIsRejectedAndDoesNotCreateARecord() {
        Order order=sampleOrder();
        tracking.recordPlaced(order);
        Long id=order.getOrderId();
        assertThrows(ResponseStatusException.class,()->
            tracking.updateAdmin(id,new OrderTrackingService.UpdateRequest("SHIPPED",null,null,null,null)));
        assertEquals(1,eventRepo.findByOrderOrderIdOrderByOccurredAtAscIdAsc(id).size());
    }
}
