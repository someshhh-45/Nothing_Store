package com.eshop.Ecommerce.Controller;
import com.eshop.Ecommerce.Service.StripeCheckoutGateway;
import com.stripe.exception.StripeException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class StripeCheckoutController {
 private final StripeCheckoutGateway gateway;
 private final String signingSecret;
 public StripeCheckoutController(StripeCheckoutGateway gateway,
   @Value("${stripe.webhook.secret:}") String signingSecret){
  this.gateway=gateway;this.signingSecret=signingSecret;
 }
 public record CheckoutRequest(Long addressId){}
 @GetMapping("/api/checkout/stripe/config")
 public Map<String,Boolean> config(){return Map.of("enabled",gateway.enabled());}
 @PostMapping("/api/checkout/stripe/session")
 public StripeCheckoutGateway.CheckoutLink start(@RequestBody CheckoutRequest request)throws StripeException{
  if(request.addressId()==null)throw new org.springframework.web.server.ResponseStatusException(
    HttpStatus.BAD_REQUEST,"Choose a delivery address");
  return gateway.create(request.addressId());
 }
 @GetMapping("/api/checkout/stripe/status")
 public StripeCheckoutGateway.CheckoutStatus status(@RequestParam String sessionId){
  return gateway.status(sessionId);
 }
 @PostMapping(value="/api/stripe/webhook",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<String> webhook(@RequestBody String payload,
  @RequestHeader(value="Stripe-Signature",required=false) String signature)throws Exception{
  if(signingSecret.isBlank()||signature==null)
   return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook configuration or signature missing");
  final Event event;
  try{event=Webhook.constructEvent(payload,signature,signingSecret);}
  catch(SignatureVerificationException ex){
   return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Stripe signature");
  }
  if("checkout.session.completed".equals(event.getType())||
     "checkout.session.async_payment_succeeded".equals(event.getType())){
   // Fulfill only when the verified signed event indicates payment_status=paid.
   // Asynchronous payment methods may emit checkout.session.completed before
   // funds settle; only process an event explicitly marked paid.
   if(new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload)
      .path("data").path("object").path("payment_status").asText().equals("paid"))
      gateway.fulfillPaidCheckout(payload);
  }
  return ResponseEntity.ok("ok");
 }
}
