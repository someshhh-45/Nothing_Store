package com.eshop.Ecommerce.extension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable at-least-once publisher; multiple instances use atomic SQL claims. */
@Component
public class OutboxPublisher {
 private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
 private final JdbcTemplate jdbc;
 private final KafkaTemplate<String,String> kafka;
 private final int batchSize;
 private final long timeoutSeconds;
 public OutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,
   @Value("${OUTBOX_BATCH_SIZE:40}") int batchSize,
   @Value("${OUTBOX_SEND_TIMEOUT_SECONDS:12}") long timeoutSeconds) {
   this.jdbc=jdbc;this.kafka=kafka;
   this.batchSize=Math.max(1,Math.min(batchSize,200));
   this.timeoutSeconds=Math.max(1,Math.min(timeoutSeconds,30));
 }
 @Scheduled(fixedDelayString="${OUTBOX_POLL_MS:2000}")
 public void publish() {
  var rows=jdbc.queryForList(
   "select id,topic,event_key,payload::text as payload from nothing_outbox " +
   "where published_at is null and next_attempt_at<=now() " +
   "and (claimed_at is null or claimed_at < now()-interval '2 minutes') " +
   "order by created_at limit ?",batchSize);
  for(var row:rows){
   UUID id=(UUID)row.get("id");
   if(jdbc.update("update nothing_outbox set claimed_at=now() where id=? and published_at is null " +
    "and next_attempt_at<=now() and (claimed_at is null or claimed_at < now()-interval '2 minutes')",id)!=1)continue;
   try {
    kafka.send((String)row.get("topic"),(String)row.get("event_key"),(String)row.get("payload"))
      .get(timeoutSeconds,TimeUnit.SECONDS);
    jdbc.update("update nothing_outbox set published_at=now(),claimed_at=null where id=?",id);
   }catch(Exception ex){
    // 1, 2, 4 ... 256 second persisted retry delay; events are never discarded.
    jdbc.update("update nothing_outbox set claimed_at=null,attempt_count=attempt_count+1," +
      "next_attempt_at=now() + power(2,least(attempt_count,8)) * interval '1 second' " +
      "where id=? and published_at is null",id);
    log.warn("Outbox event {} retained for retry after Kafka failure: {}",id,ex.toString());
    break;
   }
  }
 }
}
