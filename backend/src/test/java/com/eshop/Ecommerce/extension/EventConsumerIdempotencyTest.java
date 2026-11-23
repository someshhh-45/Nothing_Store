package com.eshop.Ecommerce.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EventConsumerIdempotencyTest {
 @Autowired EventConsumer consumer;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper mapper;
 @MockBean OpenSearchCatalog search;
 @MockBean RabbitTemplate rabbit;

 @Test void searchOutageRollsBackInboxThenReplaySucceedsOnlyOnce() throws Exception {
  UUID id=UUID.randomUUID();
  long productId=9876543L;
  String event=mapper.writeValueAsString(Map.of("eventId",id.toString(),"type","PRODUCT_CHANGED","productId",productId));
  doThrow(new IllegalStateException("search unavailable")).when(search).index(productId);
  assertThrows(IllegalStateException.class,()->consumer.consume(event));
  assertEquals(0,jdbc.queryForObject("select count(*) from nothing_consumed where event_id=?",Integer.class,id));
  reset(search);
  consumer.consume(event);
  consumer.consume(event);
  assertEquals(1,jdbc.queryForObject("select count(*) from nothing_consumed where event_id=?",Integer.class,id));
  verify(search,times(1)).index(productId);
 }
}
