package com.eshop.Ecommerce.extension;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * PostgreSQL is the durable source of truth for sending confirmation mail.
 * Atomic row claims make concurrent Spring replicas and duplicate Rabbit events safe.
 * Email-provider delivery is outside the DB transaction; exact-once delivery cannot be guaranteed if
 * the process crashes after SMTP accepts the message but before marking SENT.
 */
@Component
@ConditionalOnProperty(name="nothing.notifications.mail-enabled",havingValue="true")
public class OrderEmailDispatcher {
 private static final Logger log=LoggerFactory.getLogger(OrderEmailDispatcher.class);
 private final JdbcTemplate jdbc;
 private final OrderConfirmationComposer composer;
 private final JavaMailSender mail;
 private final String from;
 private final int batchLimit;

 public OrderEmailDispatcher(JdbcTemplate jdbc,OrderConfirmationComposer composer,JavaMailSender mail,
   @Value("${nothing.mail.from:}") String from,
   @Value("${NOTIFICATION_BATCH_SIZE:12}") int batchLimit,
   @Value("${SMTP_HOST:}") String smtpHost,
   @Value("${nothing.mail.provider:smtp}") String provider){
  this.jdbc=jdbc;this.composer=composer;this.mail=mail;this.from=from;
  this.batchLimit=Math.max(1,Math.min(batchLimit,50));
  if(from==null||from.isBlank()||(!"gmail-api".equalsIgnoreCase(provider)
      && (smtpHost==null||smtpHost.isBlank())))
   throw new IllegalStateException("Email notifications require EMAIL_FROM and a configured SMTP or Gmail API provider");
 }

 /** A duplicate / concurrent attempt is a no-op, not a second email. */
 public void deliver(Long id){
  if(id==null||id<1)return;
  // If an SMTP call crashed, another replica may retry after the lease expires.
  // Keep the 2-minute lease above configured SMTP timeouts.
  int owned=jdbc.update(
   "update order_confirmation_emails set status='SENDING', claimed_at=now(), attempts=attempts+1 " +
   "where order_id=? and " +
   "((status='PENDING' and next_attempt_at<=now()) or " +
   " (status='SENDING' and claimed_at < now()-interval '2 minutes'))",id);
  if(owned!=1)return;
  try {
   OrderConfirmationComposer.Confirmation confirmation=composer.compose(id);
   MimeMessage message=mail.createMimeMessage();
   MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
   helper.setFrom(from);
   helper.setTo(confirmation.to());
   helper.setSubject(confirmation.subject());
   helper.setText(confirmation.plainText(),confirmation.htmlText());
   mail.send(message);
   if(jdbc.update("update order_confirmation_emails set status='SENT',sent_at=now()," +
       "claimed_at=null,last_error_code=null where order_id=? and status='SENDING'",id)!=1)
     throw new IllegalStateException("Email was sent but delivery state could not be recorded");
   log.info("Order {} confirmation accepted by outgoing mail provider",id);
  }catch(Exception ex){
   // Avoid logging SMTP credentials, email addresses or provider responses.
   jdbc.update("update order_confirmation_emails set status='PENDING',claimed_at=null," +
     "next_attempt_at=now() + power(2,least(attempts,9))*interval '1 second'," +
     "last_error_code=? where order_id=? and status='SENDING'",
      ex.getClass().getSimpleName(),id);
   log.warn("Order {} confirmation pending retry ({})",id,ex.getClass().getSimpleName());
  }
 }

 /** Scheduled fallback also delivers orders when Kafka/RabbitMQ is delayed or down. */
 public void deliverDue(){
  List<Long> due=jdbc.queryForList(
   "select order_id from order_confirmation_emails where " +
   "(status='PENDING' and next_attempt_at<=now()) or " +
   "(status='SENDING' and claimed_at < now()-interval '2 minutes') " +
   "order by next_attempt_at,order_id limit ?",Long.class,batchLimit);
  for(Long id:due){
   try{deliver(id);}catch(Exception ex){
    // Never suppress DB failures in Rabbit listener; periodic fallback will retry.
    log.warn("Order {} email delivery deferred ({})",id,ex.getClass().getSimpleName());
   }
  }
 }
}
