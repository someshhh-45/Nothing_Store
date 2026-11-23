package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.extension.OrderTrackingService.TrackingView;
import com.eshop.Ecommerce.extension.OrderTrackingService.UpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderTrackingController {
    private final OrderTrackingService tracking;
    public OrderTrackingController(OrderTrackingService tracking){this.tracking=tracking;}

    @GetMapping("/api/customer/orders/{id}/tracking")
    public TrackingView mine(@PathVariable Long id){return tracking.customerTracking(id);}

    @GetMapping("/api/admin/orders/{id}/tracking")
    public TrackingView admin(@PathVariable Long id){return tracking.adminTracking(id);}

    @PutMapping("/api/admin/orders/{id}/tracking")
    public TrackingView advance(@PathVariable Long id,@Valid @RequestBody UpdateRequest update){
        tracking.updateAdmin(id,update);
        return tracking.adminTracking(id);
    }

    @PutMapping("/api/seller/orders/{id}/tracking")
    public TrackingView seller(@PathVariable Long id,@Valid @RequestBody UpdateRequest update){
        tracking.updateSeller(id,update);
        return tracking.adminTracking(id);
    }
}
