package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.OrderTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderTrackingEventRepository extends JpaRepository<OrderTrackingEvent,Long> {
    List<OrderTrackingEvent> findByOrderOrderIdOrderByOccurredAtAscIdAsc(Long orderId);
}
