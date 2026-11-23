package com.eshop.Ecommerce.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactional inbox deduplicates Kafka redelivery via PostgreSQL unique key. */
@Component
public class EventConsumer {
 private final ObjectMapper mapper; private final JdbcTemplate jdbc;
 private final OpenSearchCatalog search; private final RabbitTemplate rabbit;
 public EventConsumer(ObjectMapper mapper,JdbcTemplate jdbc,OpenSearchCatalog search,RabbitTemplate rabbit){
  this.mapper=mapper;this.jdbc=jdbc;this.search=search;this.rabbit=rabbit;
 }
 @Transactional
 @KafkaListener(topics="${nothing.events.topic}",groupId="${spring.kafka.consumer.group-id}")
 public void consume(String message) throws Exception{
  var evt=mapper.readTree(message);
  UUID id=UUID.fromString(evt.path("eventId").asText());
  if(jdbc.update("insert into nothing_consumed(event_id) values (?) on conflict do nothing",id)==0)return;
  String type=evt.path("type").asText();
  long productId=evt.path("productId").asLong();
  if("PRODUCT_CHANGED".equals(type)||"INVENTORY_CHANGED".equals(type))search.index(productId);
  else if("PRODUCT_DELETED".equals(type))search.delete(productId);
  else if("ORDER_PLACED".equals(type))
   rabbit.convertAndSend("nothing.notifications","Order "+evt.path("orderId").asLong()+" placed");
  else throw new IllegalArgumentException("Unsupported event type");
  // On downstream error this DB insert rolls back, so Kafka may retry safely.
  // External Rabbit/search side effects remain at-least-once, not exactly-once.
 }
}
