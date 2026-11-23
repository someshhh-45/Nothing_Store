package com.eshop.Ecommerce.extension;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class OrderTrackingStageTest {
    @Test void acceptsLegacyOrderStatusAndHumanStageNames(){
        assertEquals(OrderTrackingStage.PLACED,OrderTrackingStage.parse("Accepted"));
        assertEquals(OrderTrackingStage.OUT_FOR_DELIVERY,OrderTrackingStage.parse("Out for Delivery"));
        assertThrows(ResponseStatusException.class,()->OrderTrackingStage.parse("Paid"));
    }
    @Test void preventsSkippedOrBackwardsStages(){
        assertTrue(OrderTrackingStage.PLACED.canAdvanceTo(OrderTrackingStage.CONFIRMED));
        assertTrue(OrderTrackingStage.CONFIRMED.canAdvanceTo(OrderTrackingStage.PACKED));
        assertTrue(OrderTrackingStage.PACKED.canAdvanceTo(OrderTrackingStage.SHIPPED));
        assertTrue(OrderTrackingStage.SHIPPED.canAdvanceTo(OrderTrackingStage.OUT_FOR_DELIVERY));
        assertTrue(OrderTrackingStage.OUT_FOR_DELIVERY.canAdvanceTo(OrderTrackingStage.DELIVERED));
        assertFalse(OrderTrackingStage.PLACED.canAdvanceTo(OrderTrackingStage.SHIPPED));
        assertFalse(OrderTrackingStage.DELIVERED.canAdvanceTo(OrderTrackingStage.PACKED));
        assertFalse(OrderTrackingStage.CANCELLED.canAdvanceTo(OrderTrackingStage.PLACED));
    }
}
