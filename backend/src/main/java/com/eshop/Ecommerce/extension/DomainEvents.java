package com.eshop.Ecommerce.extension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Component
public class DomainEvents {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  @Value("${nothing.events.topic}") private String topic;
  public DomainEvents(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
  public void record(String type,Long productId,Long orderId){
    try{
      String id=UUID.randomUUID().toString();
      Map<String,Object> data=new LinkedHashMap<>();
      data.put("eventId",id);data.put("type",type);data.put("productId",productId);data.put("orderId",orderId);
      jdbc.update("insert into nothing_outbox(id,topic,event_key,payload) values (?::uuid,?,?,?::jsonb)",
         id,topic,productId==null?"order-"+orderId:"product-"+productId,json.writeValueAsString(data));
    }catch(Exception e){throw new IllegalStateException("Failed to save transactional event",e);}
  }
}