package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Controller.StripeCheckoutController;
import com.eshop.Ecommerce.Service.StripeCheckoutGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline Stripe signature verification; never calls Stripe or charges a card. */
class StripeSignedWebhookTest {
 private static final String KEY="whsec_local_unit_test_only";
 private static final String EVENT="{\"id\":\"evt_test_paid\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test\",\"object\":\"checkout.session\",\"payment_status\":\"paid\"}}}";

 private static String sign(String payload)throws Exception {
  long timestamp=Instant.now().getEpochSecond();
  Mac hmac=Mac.getInstance("HmacSHA256");
  hmac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  String signature=HexFormat.of().formatHex(hmac.doFinal((timestamp+"."+payload).getBytes(StandardCharsets.UTF_8)));
  return "t="+timestamp+",v1="+signature;
 }

 @Test void verifiedPaidCheckoutEventTriggersFulfillment()throws Exception{
  StripeCheckoutGateway gateway=mock(StripeCheckoutGateway.class);
  StripeCheckoutController controller=new StripeCheckoutController(gateway,KEY);
  assertEquals(200,controller.webhook(EVENT,sign(EVENT)).getStatusCode().value());
  verify(gateway,times(1)).fulfillPaidCheckout(EVENT);
 }

 @Test void invalidSignatureNeverTriggersFulfillment()throws Exception{
  StripeCheckoutGateway gateway=mock(StripeCheckoutGateway.class);
  StripeCheckoutController controller=new StripeCheckoutController(gateway,KEY);
  assertEquals(400,controller.webhook(EVENT,"t=1,v1=invalid").getStatusCode().value());
  verifyNoInteractions(gateway);
 }

 @Test void unpaidEventIsAcknowledgedButNotFulfilled()throws Exception{
  StripeCheckoutGateway gateway=mock(StripeCheckoutGateway.class);
  StripeCheckoutController controller=new StripeCheckoutController(gateway,KEY);
  String unpaid=EVENT.replace("\"paid\"","\"unpaid\"");
  assertEquals(200,controller.webhook(unpaid,sign(unpaid)).getStatusCode().value());
  verifyNoInteractions(gateway);
 }
}
