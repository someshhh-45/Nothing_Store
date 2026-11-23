package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Order;
import com.eshop.Ecommerce.Model.OrderItem;
import com.eshop.Ecommerce.Model.Payment;
import com.eshop.Ecommerce.Repositories.OrderRepo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderConfirmationComposerTest {
 @Test void usesCheckoutSnapshotsAndEscapesMarkupWhileShowingDiscount() {
  OrderRepo orders=mock(OrderRepo.class);
  Order order=new Order();order.setOrderId(42L);order.setEmail("buyer@example.org");
  order.setTotalAmount(799.0);order.setOrderSubtotal(899.0);
  order.setDiscountAmount(100.0);order.setOfferCode("WELCOME100");
  order.setShippingAddressSnapshot("Building <A>, Bengaluru & Main Road");
  order.setPayment(new Payment("CASH_ON_DELIVERY",null,"PENDING","Pending","COD"));
  OrderItem item=new OrderItem();item.setQuantity(1);
  item.setOrderedProductName("<script>alert(1)</script> Earbuds");
  item.setOrderedProductPrice(899.0);order.setOrderItems(List.of(item));
  when(orders.findById(42L)).thenReturn(Optional.of(order));
  var mail=new OrderConfirmationComposer(orders).compose(42L);
  assertEquals("buyer@example.org",mail.to());
  assertTrue(mail.subject().contains("42"));
  assertTrue(mail.plainText().contains("INR 799.00"));
  assertTrue(mail.plainText().contains("WELCOME100"));
  assertTrue(mail.plainText().contains("Cash on delivery"));
  assertTrue(mail.htmlText().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
  assertTrue(mail.htmlText().contains("Building &lt;A&gt;, Bengaluru &amp; Main Road"));
  assertFalse(mail.htmlText().contains("<script>"));
 }
 @Test void verifiedStripeUsesPaidWordingNotPayOnDelivery(){
  OrderRepo orders=mock(OrderRepo.class);
  Order order=new Order();order.setOrderId(43L);order.setEmail("buyer@example.org");
  order.setTotalAmount(499.0);order.setPayment(
    new Payment("STRIPE","cs_test_43","PAID","Verified","Stripe"));
  order.setOrderItems(List.of());
  when(orders.findById(43L)).thenReturn(Optional.of(order));
  var receipt=new OrderConfirmationComposer(orders).compose(43L);
  assertTrue(receipt.plainText().contains("Card payment received"));
  assertFalse(receipt.plainText().contains("payment due"));
 }
}
