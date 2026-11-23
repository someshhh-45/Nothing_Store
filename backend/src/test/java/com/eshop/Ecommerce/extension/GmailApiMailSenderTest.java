package com.eshop.Ecommerce.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** All Google calls are mocked; CI never needs credentials or sends real mail. */
class GmailApiMailSenderTest {
 private final HttpClient http=mock(HttpClient.class);
 private final ObjectMapper mapper=new ObjectMapper();
 private GmailApiMailSender sender(){
  return new GmailApiMailSender(
   "desktop-client-id","private-client-secret","private-refresh-token",
   "orders@example.org",mapper,http);
 }
 @SuppressWarnings("unchecked")
 private HttpResponse<String> response(int status,String body){
  HttpResponse<String> r=(HttpResponse<String>)mock(HttpResponse.class);
  when(r.statusCode()).thenReturn(status);
  when(r.body()).thenReturn(body);
  return r;
 }
 private void mockSuccess()throws Exception{
  HttpResponse<String> tokenReply=response(200,"{\"access_token\":\"fake-access\",\"expires_in\":3599}");
  HttpResponse<String> sentReply=response(200,"{\"id\":\"gmail-id-42\",\"threadId\":\"t-42\"}");
  when(http.send(any(HttpRequest.class),
    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
   .thenReturn(tokenReply,sentReply);
 }
 @Test void sendsHtmlAndPlainTextViaOnlyGmailApiWithOAuthRefresh()throws Exception{
  mockSuccess();
  GmailApiMailSender sender=sender();
  MimeMessage mime=sender.createMimeMessage();
  MimeMessageHelper helper=new MimeMessageHelper(mime,true,"UTF-8");
  helper.setFrom("orders@example.org");
  helper.setTo("buyer@example.org");
  helper.setSubject("Order #42 confirmed");
  helper.setText("Payment due on delivery","<b>Payment due on delivery</b>");
  sender.send(mime);
  var requests=ArgumentCaptor.forClass(HttpRequest.class);
  verify(http,times(2)).send(requests.capture(),
    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  HttpRequest token=requests.getAllValues().get(0);
  HttpRequest email=requests.getAllValues().get(1);
  assertEquals("https://oauth2.googleapis.com/token",token.uri().toString());
  assertEquals("https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
    email.uri().toString());
  assertEquals("Bearer fake-access",email.headers().firstValue("Authorization").orElseThrow());
  String payload=readBody(email);
  String raw=mapper.readTree(payload).path("raw").asText();
  String content=new String(Base64.getUrlDecoder().decode(raw),StandardCharsets.UTF_8);
  assertTrue(content.contains("buyer@example.org"));
  assertTrue(content.contains("multipart/alternative"));
  assertFalse(payload.contains("private-refresh-token"));
 }
 @Test void signupOtpAlsoWorksWithoutSmtp()throws Exception{
  mockSuccess();
  SimpleMailMessage otp=new SimpleMailMessage();
  otp.setFrom("orders@example.org");otp.setTo("buyer@example.org");
  otp.setSubject("Verify your email");otp.setText("Your verification code is 123456");
  sender().send(otp);
  verify(http,times(2)).send(any(HttpRequest.class),
    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
 }
 @Test void rejectedGoogleRefreshFailsClosedWithoutLeakingSecrets()throws Exception{
  HttpResponse<String> invalidToken=response(400,"{\"error\":\"invalid_grant\"}");
  when(http.send(any(HttpRequest.class),
    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
   .thenReturn(invalidToken);
  SimpleMailMessage otp=new SimpleMailMessage();
  otp.setTo("buyer@example.org");otp.setSubject("OTP");otp.setText("Code 123456");
  MailAuthenticationException ex=assertThrows(MailAuthenticationException.class,
   ()->sender().send(otp));
  assertFalse(ex.getMessage().contains("private-refresh-token"));
  verify(http,times(1)).send(any(HttpRequest.class),
    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
 }
 @Test void missingGoogleCredentialsFailStartup(){
  assertThrows(IllegalStateException.class,
   ()->new GmailApiMailSender("id","","refresh","sender@example.org",mapper,http));
 }
 private static String readBody(HttpRequest request){
  HttpRequest.BodyPublisher publisher=request.bodyPublisher().orElseThrow();
  var buffer=new java.io.ByteArrayOutputStream();
  java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> subscriber =
   new java.util.concurrent.Flow.Subscriber<>() {
    public void onSubscribe(java.util.concurrent.Flow.Subscription s){s.request(Long.MAX_VALUE);}
    public void onNext(java.nio.ByteBuffer b){
     byte[] bytes=new byte[b.remaining()];b.get(bytes);buffer.writeBytes(bytes);
    }
    public void onError(Throwable t){throw new IllegalStateException(t);}
    public void onComplete(){}
   };
  publisher.subscribe(subscriber);
  return buffer.toString(StandardCharsets.UTF_8);
 }
}
