package com.eshop.Ecommerce.extension;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/** Live SASL/TLS broker and topic probe; never emits credentials in responses. */
@Component("kafkaBrokerHealthIndicator")
public class KafkaBrokerHealthIndicator implements HealthIndicator {
 private final KafkaAdmin admin; private final String topic;
 public KafkaBrokerHealthIndicator(KafkaAdmin admin,@Value("${nothing.events.topic}")String topic){
  this.admin=admin;this.topic=topic;
 }
 @Override public Health health(){
  try(AdminClient client=AdminClient.create(admin.getConfigurationProperties())){
   client.describeCluster().clusterId().get(4,TimeUnit.SECONDS);
   client.describeTopics(List.of(topic)).allTopicNames().get(4,TimeUnit.SECONDS);
   return Health.up().build();
  }catch(Exception ex){return Health.down().build();}
 }
}
