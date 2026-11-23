package com.eshop.Ecommerce.extension;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declare the durable queue even while the SMTP worker is disabled. */
@Configuration
public class NotificationQueueConfiguration {
 @Bean
 Queue notificationQueue(){return new Queue("nothing.notifications",true);}
}
