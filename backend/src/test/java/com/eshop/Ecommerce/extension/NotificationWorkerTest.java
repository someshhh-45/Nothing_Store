package com.eshop.Ecommerce.extension;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class NotificationWorkerTest {
 @Test void rabbitEventTriggersDeliveryAndMalformedMessagesAreIgnored(){
  OrderEmailDispatcher dispatcher=mock(OrderEmailDispatcher.class);
  NotificationWorker worker=new NotificationWorker(dispatcher);
  worker.notifyCustomer("Order 42 placed");
  worker.notifyCustomer("not an order");
  worker.notifyCustomer(null);
  verify(dispatcher,times(1)).deliver(42L);
  verifyNoMoreInteractions(dispatcher);
  worker.retryPending();
  verify(dispatcher,times(1)).deliverDue();
 }
}
