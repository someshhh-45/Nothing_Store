package com.eshop.Ecommerce.extension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Authenticated cluster HTTP request, including Aiven HTTPS certificate verification. */
@Component("searchClusterHealthIndicator")
public class SearchClusterHealthIndicator implements HealthIndicator {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private final String endpoint,auth;
 public SearchClusterHealthIndicator(@Value("${nothing.search.url}")String endpoint,
    @Value("${nothing.search.username:}")String user,@Value("${nothing.search.password:}")String password){
  this.endpoint=endpoint.replaceAll("/$","");
  this.auth=user.isBlank()?"":"Basic "+Base64.getEncoder().encodeToString((user+":"+password).getBytes(StandardCharsets.UTF_8));
 }
 @Override public Health health(){
  try{
   var request=HttpRequest.newBuilder(URI.create(endpoint+"/_cluster/health")).timeout(Duration.ofSeconds(5)).GET();
   if(!auth.isBlank())request.header("Authorization",auth);
   return client.send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200?
      Health.up().build():Health.down().build();
  }catch(Exception ex){return Health.down().build();}
 }
}
