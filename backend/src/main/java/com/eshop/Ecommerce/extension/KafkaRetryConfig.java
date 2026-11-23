package com.eshop.Ecommerce.extension;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Retain failed Kafka records on the partition until dependencies recover. */
@Configuration
public class KafkaRetryConfig {
 @Bean
 public DefaultErrorHandler marketplaceKafkaErrorHandler() {
  return new DefaultErrorHandler(new FixedBackOff(2000L,FixedBackOff.UNLIMITED_ATTEMPTS));
 }
}
