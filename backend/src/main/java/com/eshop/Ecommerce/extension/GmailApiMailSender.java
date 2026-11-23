package com.eshop.Ecommerce.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/**
 * Google Cloud OAuth 2.0 Gmail API sender for a single consenting mail account.
 * Uses only gmail.send and Java's HttpClient (no SMTP or extra dependencies).
 * The OAuth refresh token is a secret supplied by environment at deployment.
 *
 * The same JavaMailSender interface powers signup OTPs and HTML order receipts.
 */
@Component
@Primary
@ConditionalOnProperty(name="nothing.mail.provider",havingValue="gmail-api")
public class GmailApiMailSender implements JavaMailSender {
 private static final URI OAUTH_TOKEN=URI.create("https://oauth2.googleapis.com/token");
 private static final URI GMAIL_SEND=URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
 private final String clientId;
 private final String clientSecret;
 private final String refreshToken;
 private final String from;
 private final ObjectMapper json;
 private final HttpClient http;

 @Autowired
 public GmailApiMailSender(
   @Value("${GMAIL_OAUTH_CLIENT_ID:}") String clientId,
   @Value("${GMAIL_OAUTH_CLIENT_SECRET:}") String clientSecret,
   @Value("${GMAIL_OAUTH_REFRESH_TOKEN:}") String refreshToken,
   @Value("${EMAIL_FROM:}") String from,
   ObjectMapper json) {
  this(clientId,clientSecret,refreshToken,from,json,
   HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NEVER).build());
 }

 // Tests can substitute HTTP without contacting a real Google account.
 GmailApiMailSender(String clientId,String clientSecret,String refreshToken,
   String from,ObjectMapper json,HttpClient http){
  this.clientId=clientId;this.clientSecret=clientSecret;
  this.refreshToken=refreshToken;this.from=from;this.json=json;this.http=http;
  if(blank(clientId)||blank(clientSecret)||blank(refreshToken)||blank(from))
   throw new IllegalStateException(
    "MAIL_PROVIDER=gmail-api requires GMAIL_OAUTH_CLIENT_ID, GMAIL_OAUTH_CLIENT_SECRET, GMAIL_OAUTH_REFRESH_TOKEN and EMAIL_FROM");
 }
 private static boolean blank(String s){return s==null||s.isBlank();}

 @Override public MimeMessage createMimeMessage() {
  return new MimeMessage(Session.getInstance(new Properties()));
 }
 @Override public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
  try{return new MimeMessage(Session.getInstance(new Properties()),contentStream);}
  catch(MessagingException ex){throw new MailParseException(ex);}
 }
 @Override public void send(MimeMessage mimeMessage) throws MailException {
  if(mimeMessage==null)throw new MailPreparationException(new IllegalArgumentException("Null email"));
  try{
   // A single Gmail account can send only as itself or a configured send-as alias.
   // Sender addresses are never taken from a customer's checkout request.
   ByteArrayOutputStream stream=new ByteArrayOutputStream();
   mimeMessage.saveChanges();
   mimeMessage.writeTo(stream);
   String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(stream.toByteArray());
   String accessToken=accessToken();
   HttpRequest request=HttpRequest.newBuilder(GMAIL_SEND)
    .timeout(Duration.ofSeconds(20))
    .header("Authorization","Bearer "+accessToken)
    .header("Content-Type","application/json; charset=UTF-8")
    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
      java.util.Map.of("raw",raw)),StandardCharsets.UTF_8)).build();
   HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
   // Do not log response bodies: they may contain private message metadata.
   if(response.statusCode()==401||response.statusCode()==403)
    throw new MailAuthenticationException("Gmail API refused send authorization (HTTP "+response.statusCode()+")");
   if(response.statusCode()<200||response.statusCode()>=300)
    throw new MailSendException("Gmail API send failed (HTTP "+response.statusCode()+")");
   JsonNode ack=json.readTree(response.body());
   if(blank(ack.path("id").asText("")))
    throw new MailSendException("Gmail API send returned no message ID");
  }catch(MailException ex){throw ex;}
   catch(InterruptedException ex){
    Thread.currentThread().interrupt();
    throw new MailSendException("Gmail API delivery interrupted",ex);
   }catch(Exception ex){
    throw new MailSendException("Gmail API delivery failed ("+ex.getClass().getSimpleName()+")",ex);
   }
 }
 private String accessToken(){
  try{
   String body="client_id="+encode(clientId)+"&client_secret="+encode(clientSecret)
     +"&refresh_token="+encode(refreshToken)+"&grant_type=refresh_token";
   HttpRequest request=HttpRequest.newBuilder(OAUTH_TOKEN)
     .timeout(Duration.ofSeconds(15))
     .header("Content-Type","application/x-www-form-urlencoded")
     .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
   HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300)
    throw new MailAuthenticationException(
     "Google OAuth refresh failed (HTTP "+response.statusCode()+"). Check consent or expired token.");
   String token=json.readTree(response.body()).path("access_token").asText("");
   if(blank(token))throw new MailAuthenticationException("Google OAuth refresh returned no access token");
   return token;
  }catch(MailException ex){throw ex;}
   catch(InterruptedException ex){
    Thread.currentThread().interrupt();
    throw new MailAuthenticationException("Google OAuth refresh interrupted",ex);
   }catch(Exception ex){
    throw new MailAuthenticationException("Google OAuth refresh unavailable",ex);
   }
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}

 @Override public void send(MimeMessage... messages) throws MailException {
  for(MimeMessage m:messages)send(m);
 }
 @Override public void send(MimeMessagePreparator preparator) throws MailException {
  try{
   MimeMessage m=createMimeMessage();
   preparator.prepare(m);
   send(m);
  }catch(MailException ex){throw ex;}
   catch(Exception ex){throw new MailPreparationException(ex);}
 }
 @Override public void send(MimeMessagePreparator... preparators) throws MailException {
  for(MimeMessagePreparator p:preparators)send(p);
 }
 @Override public void send(SimpleMailMessage simple) throws MailException {
  if(simple==null)throw new MailPreparationException(new IllegalArgumentException("Null email"));
  try{
   MimeMessage m=createMimeMessage();
   MimeMessageHelper h=new MimeMessageHelper(m,false,"UTF-8");
   h.setFrom(blank(simple.getFrom())?from:simple.getFrom());
   if(simple.getTo()==null||simple.getTo().length==0)
    throw new IllegalArgumentException("Missing recipient");
   h.setTo(simple.getTo());
   if(simple.getCc()!=null)h.setCc(simple.getCc());
   if(simple.getBcc()!=null)h.setBcc(simple.getBcc());
   if(simple.getReplyTo()!=null)h.setReplyTo(simple.getReplyTo());
   h.setSubject(simple.getSubject()==null?"":simple.getSubject());
   h.setText(simple.getText()==null?"":simple.getText());
   if(simple.getSentDate()!=null)h.setSentDate(simple.getSentDate());
   send(m);
  }catch(MailException ex){throw ex;}
   catch(Exception ex){throw new MailPreparationException(ex);}
 }
 @Override public void send(SimpleMailMessage... messages) throws MailException {
  for(SimpleMailMessage m:messages)send(m);
 }
}
