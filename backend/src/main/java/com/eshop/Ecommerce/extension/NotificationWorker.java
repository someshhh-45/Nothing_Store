package com.eshop.Ecommerce.extension;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RabbitMQ event wakes the durable DB-backed email dispatcher.
 * Polling is a fallback for broker failures / delivery lost before Rabbit ACK.
 * Disabled until a real SMTP provider is configured.
 */
@Component
@ConditionalOnProperty(name="nothing.notifications.mail-enabled",havingValue="true")
public class NotificationWorker {
 private static final Pattern ORDER_MESSAGE=Pattern.compile("^Order ([0-9]{1,19}) placed$");
 private final OrderEmailDispatcher deliveries;
 public NotificationWorker(OrderEmailDispatcher deliveries){this.deliveries=deliveries;}

 @RabbitListener(queues="nothing.notifications")
 public void notifyCustomer(String message){
  if(message==null)return;
  Matcher m=ORDER_MESSAGE.matcher(message);
  if(!m.matches())return; // do not poison/block the queue with unsupported events
  // If the DB is unavailable the Rabbit listener throws, so Rabbit can retry.
  deliveries.deliver(Long.valueOf(m.group(1)));
 }

 @Scheduled(fixedDelayString="${NOTIFICATION_POLL_MS:5000}")
 public void retryPending(){deliveries.deliverDue();}
}
