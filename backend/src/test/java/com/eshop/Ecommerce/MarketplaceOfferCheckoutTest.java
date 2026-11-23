package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.CartOfferService;
import com.eshop.Ecommerce.Service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="SEED_OFFERS=false")
class MarketplaceOfferCheckoutTest {
 @Autowired UserRepository users;
 @Autowired CategoryRepo categories;
 @Autowired ProductRepo products;
 @Autowired CartRepository carts;
 @Autowired CartItemRepository cartItems;
 @Autowired AddressRepository addresses;
 @Autowired MarketplaceOfferRepository offers;
 @Autowired CartOfferService pricing;
 @Autowired OrderService orderService;
 @Autowired OrderRepo orders;
 @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

 private CartOfferService.Quote quote(Bag bag,boolean strict){
  return transactions.execute(status->pricing.quote(
    carts.findCartByEmail(bag.user().getEmail()),bag.user().getEmail(),strict));
 }

 private record Bag(User user,Cart cart,Product product,Address address){}
 private Bag bag(String marker,int quantity,double price) {
  User buyer=users.save(new User("deal"+marker,"deal"+marker+"@example.org","test-unused-hash"));
  Category cat=new Category();cat.setCategoryName("Offer Test "+marker);cat=categories.save(cat);
  Product product=new Product();product.setProductName("Checkout Offer Product "+marker);
  product.setDescription("A product used for checkout and promotion tests");
  product.setQuantity(7);product.setPrice(price);product.setSpecialPrice(price);
  product.setCategory(cat);product=products.saveAndFlush(product);
  Address address=new Address("Testing Street Number One","Sample Building A",
   "Bengaluru","Karnataka","India","560001");
  address.setUser(buyer);address=addresses.save(address);
  Cart cart=new Cart();cart.setUser(buyer);cart.setTotalPrice(price*quantity);cart=carts.save(cart);
  CartItem line=new CartItem();line.setCart(cart);line.setProduct(product);line.setQuantity(quantity);
  line.setProductPrice(price);line.setDiscount(0.0);cartItems.saveAndFlush(line);
  return new Bag(buyer,cart,product,address);
 }
 private MarketplaceOffer campaign(String code,MarketplaceOffer.Kind kind,String value,
   String min,String max,String cap,int userLimit,boolean automatic) {
  var offer=new MarketplaceOffer();
  offer.setCode(code);offer.setTitle("Testing "+code);
  offer.setKind(kind);offer.setValue(new BigDecimal(value));
  offer.setMinSubtotal(new BigDecimal(min));
  offer.setMaxSubtotal(max==null?null:new BigDecimal(max));
  offer.setMaxDiscount(cap==null?null:new BigDecimal(cap));
  offer.setStartsAt(Instant.now().minus(1,ChronoUnit.DAYS));
  offer.setEndsAt(Instant.now().plus(3,ChronoUnit.DAYS));
  offer.setGlobalLimit(10);offer.setPerCustomerLimit(userLimit);
  offer.setAutoApply(automatic);
  return offers.saveAndFlush(offer);
 }

 @Test
 void minimumSpendAndMaximumSpendEnforcedWithPercentageCap() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Bag bag=bag(suffix,2,750);
  MarketplaceOffer offer=campaign("TIER"+suffix,MarketplaceOffer.Kind.PERCENT,
     "10","2000","3000","150",1,false);
  bag.cart().setCouponCode(offer.getCode());carts.saveAndFlush(bag.cart());
  var quote=quote(bag,false);
  assertFalse(quote.applied());assertEquals(1500,quote.total(),0.01);
  assertTrue(quote.message().contains("more"));
  assertThrows(ResponseStatusException.class,()->quote(bag,true));
  CartItem line=cartItems.findCartItemByProductIdAndCartId(bag.cart().getCartId(),bag.product().getProductId());
  line.setProductPrice(1500.0);cartItems.saveAndFlush(line);
  var eligible=quote(bag,true);
  assertEquals(3000.0,eligible.subtotal(),0.01);
  assertEquals(150.0,eligible.discount(),0.01);
  assertEquals(2850.0,eligible.total(),0.01);
  line.setProductPrice(1600.0);cartItems.saveAndFlush(line);
  assertFalse(quote(bag,false).applied());
 }

 @Test
 void codRedeemsOnceAndRejectsSecondCheckoutWithoutReducingStock() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Bag bag=bag(suffix,2,600);
  MarketplaceOffer offer=campaign("FLAT"+suffix,MarketplaceOffer.Kind.FLAT,
    "200","1000",null,"200",1,false);
  bag.cart().setCouponCode(offer.getCode());carts.saveAndFlush(bag.cart());
  var order=orderService.placeOrder(bag.user().getEmail(),bag.address().getAddressId(),
    "COD","COD",null,"PENDING","Pay on delivery");
  assertEquals(1200.0,order.getOrderSubtotal(),0.01);
  assertEquals(200.0,order.getDiscountAmount(),0.01);
  assertEquals(1000.0,order.getTotalAmount(),0.01);
  assertEquals(offer.getCode(),order.getOfferCode());
  assertEquals(5,products.findById(bag.product().getProductId()).orElseThrow().getQuantity());
  assertEquals(1,offers.findById(offer.getId()).orElseThrow().getUsedCount());
  Cart after=carts.findCartByEmail(bag.user().getEmail());
  assertNull(after.getCouponCode(),"Checkout must clear used coupon selection");
  CartItem second=new CartItem();second.setCart(after);second.setProduct(bag.product());
  second.setProductPrice(600.0);second.setDiscount(0.0);second.setQuantity(1);
  cartItems.saveAndFlush(second);
  after.setCouponCode(offer.getCode());carts.saveAndFlush(after);
  assertThrows(ResponseStatusException.class,()->orderService.placeOrder(
    bag.user().getEmail(),bag.address().getAddressId(),"COD","COD",null,"PENDING","Pay on delivery"));
  assertEquals(5,products.findById(bag.product().getProductId()).orElseThrow().getQuantity());
  assertEquals(1,offers.findById(offer.getId()).orElseThrow().getUsedCount());
  assertEquals(1,orders.findByEmailIgnoreCaseOrderByOrderDateDesc(bag.user().getEmail()).size());
 }

 @Test
 void automaticallyAppliesEligibleThresholdOfferWithoutCustomerCoupon() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Bag bag=bag(suffix,2,1000);
  var automatic=campaign("AUTO"+suffix,MarketplaceOffer.Kind.PERCENT,
    "5","1500",null,"90",3,true);
  var quote=quote(bag,true);
  assertTrue(quote.applied());assertTrue(quote.automatic());
  assertEquals(automatic.getCode(),quote.code());
  assertEquals(90.0,quote.discount(),0.01);
  assertEquals(1910.0,quote.total(),0.01);
 }
}
