package com.eshop.Ecommerce.extension;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderEmailDispatcherTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final OrderConfirmationComposer composer=mock(OrderConfirmationComposer.class);
 private final JavaMailSender mail=mock(JavaMailSender.class);
 private OrderEmailDispatcher dispatcher(){
  return new OrderEmailDispatcher(jdbc,composer,mail,"orders@example.org",12,"smtp.example.org","smtp");
 }
 private void mockReceipt() {
  when(composer.compose(42L)).thenReturn(new OrderConfirmationComposer.Confirmation(
    "buyer@example.org","NOTHING Market | Order #42 confirmed",
    "Order #42 confirmed, INR 799.00","<html><body>Order confirmed</body></html>"));
  when(mail.createMimeMessage()).thenAnswer(
    i->new MimeMessage(Session.getInstance(new Properties())));
 }
 @Test void duplicateOrderMessageSendsExactlyOnceWhenDatabaseSaysSent()throws Exception {
  mockReceipt();
  when(jdbc.update(contains("set status='SENDING'"),eq(42L))).thenReturn(1,0);
  when(jdbc.update(contains("set status='SENT'"),eq(42L))).thenReturn(1);
  OrderEmailDispatcher d=dispatcher();
  d.deliver(42L);d.deliver(42L);
  verify(composer,times(1)).compose(42L);
  verify(mail,times(1)).send(any(MimeMessage.class));
  verify(jdbc,times(1)).update(contains("set status='SENT'"),eq(42L));
 }
 @Test void temporarySmtpFailureReturnsJobToPendingRetry() {
  mockReceipt();
  when(jdbc.update(contains("set status='SENDING'"),eq(42L))).thenReturn(1);
  doThrow(new MailSendException("offline SMTP")).when(mail).send(any(MimeMessage.class));
  assertDoesNotThrow(()->dispatcher().deliver(42L));
  verify(jdbc).update(contains("set status='PENDING'"),eq("MailSendException"),eq(42L));
  verify(jdbc,never()).update(contains("set status='SENT'"),eq(42L));
 }
 @Test void disabledSmtpCannotStartSender(){
  assertThrows(IllegalStateException.class,
   ()->new OrderEmailDispatcher(jdbc,composer,mail,"",12,"smtp.example.org","smtp"));
  assertThrows(IllegalStateException.class,
   ()->new OrderEmailDispatcher(jdbc,composer,mail,"orders@example.org",12,"","smtp"));
  assertDoesNotThrow(()->new OrderEmailDispatcher(jdbc,composer,mail,
    "orders@example.org",12,"","gmail-api"));
 }
}
