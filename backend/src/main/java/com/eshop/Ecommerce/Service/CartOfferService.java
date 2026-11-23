package com.eshop.Ecommerce.Service;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.Instant;
import java.util.*;

@Service
public class CartOfferService {
 private final MarketplaceOfferRepository offers;
 private final OrderRepo orders;
 public CartOfferService(MarketplaceOfferRepository offers,OrderRepo orders){this.offers=offers;this.orders=orders;}

 public record Quote(double subtotal,double discount,double total,String code,String title,
                     boolean automatic,boolean applied,String message) {
  public long totalPaise(){return paise(total);}
 }
 private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
 public static long paise(double v){return BigDecimal.valueOf(v).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}
 public static String normalizeCode(String code){
  if(code==null||code.isBlank())return null;
  String normalized=code.trim().toUpperCase(Locale.ROOT);
  if(!normalized.matches("[A-Z0-9_-]{3,32}"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid offer code");
  return normalized;
 }
 private BigDecimal subtotal(Cart cart){
  return cart.getCartItems().stream().reduce(BigDecimal.ZERO,(s,line)->{
   if(line.getQuantity()==null||line.getQuantity()<1||line.getProductPrice()==null||line.getProductPrice()<0)
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Cart prices or quantities are invalid");
   return s.add(BigDecimal.valueOf(line.getProductPrice()).multiply(BigDecimal.valueOf(line.getQuantity())));
  },BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
 }
 private String ineligible(MarketplaceOffer offer,BigDecimal amount,String email){
  Instant now=Instant.now();
  if(!offer.isActive()||now.isBefore(offer.getStartsAt())||!now.isBefore(offer.getEndsAt()))
   return "This offer has expired or is not active";
  if(amount.compareTo(offer.getMinSubtotal())<0)
   return "Add ₹"+money(offer.getMinSubtotal().subtract(amount)).toPlainString()+" more to unlock "+offer.getCode();
  if(offer.getMaxSubtotal()!=null&&amount.compareTo(offer.getMaxSubtotal())>0)
   return "This offer is for orders up to ₹"+offer.getMaxSubtotal();
  if(offer.getGlobalLimit()!=null&&offer.getUsedCount()>=offer.getGlobalLimit())
   return "This offer has reached its redemption limit";
  if(email!=null&&offer.getPerCustomerLimit()!=null&&
    orders.countByEmailIgnoreCaseAndOfferCodeIgnoreCase(email,offer.getCode())>=offer.getPerCustomerLimit())
   return "You have already used this offer";
  return null;
 }
 private BigDecimal discount(MarketplaceOffer offer,BigDecimal subtotal){
  BigDecimal raw=offer.getKind()==MarketplaceOffer.Kind.PERCENT
   ? subtotal.multiply(offer.getValue()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
   : offer.getValue();
  if(offer.getMaxDiscount()!=null)raw=raw.min(offer.getMaxDiscount());
  // Never allow negative payable amounts or zero-amount Stripe charges.
  return money(raw.min(subtotal).max(BigDecimal.ZERO));
 }
 private Quote base(BigDecimal subtotal,String code,String message){
  double amount=subtotal.doubleValue();
  return new Quote(amount,0,amount,code,null,false,false,message);
 }
 @Transactional(readOnly=true)
 public Quote quote(Cart cart,String email,boolean enforceSelected){
  if(cart==null||cart.getCartItems().isEmpty())return new Quote(0,0,0,null,null,false,false,"Your bag is empty");
  BigDecimal subtotal=subtotal(cart);
  String selected=normalizeCode(cart.getCouponCode());
  if(selected!=null){
   MarketplaceOffer offer=offers.findByCodeIgnoreCase(selected).orElse(null);
   String issue=offer==null?"This coupon code is not available":ineligible(offer,subtotal,email);
   if(issue!=null){
    if(enforceSelected)throw new ResponseStatusException(HttpStatus.CONFLICT,issue);
    return base(subtotal,selected,issue);
   }
   return priced(subtotal,offer,false);
  }
  MarketplaceOffer best=null;
  BigDecimal bestDiscount=BigDecimal.ZERO;
  for(MarketplaceOffer candidate:offers.findByActiveTrueOrderByMinSubtotalAsc()){
   if(!candidate.isAutoApply()||ineligible(candidate,subtotal,email)!=null)continue;
   BigDecimal saving=discount(candidate,subtotal);
   if(saving.compareTo(bestDiscount)>0){best=candidate;bestDiscount=saving;}
  }
  return best==null?base(subtotal,null,"Add an eligible coupon or shop to unlock an offer"):
     priced(subtotal,best,true);
 }
 private Quote priced(BigDecimal subtotal,MarketplaceOffer offer,boolean automatic){
  BigDecimal savings=discount(offer,subtotal);
  return new Quote(subtotal.doubleValue(),savings.doubleValue(),
    subtotal.subtract(savings).doubleValue(),offer.getCode(),offer.getTitle(),
    automatic,savings.signum()>0,savings.signum()>0?"Offer applied":"No discount on this order");
 }
 /** Called inside order placement's existing DB transaction. A row lock serializes
  * global/individual redemption limits across all Spring Boot replicas.
  * The caller's transaction also persists the order and rolls back on failure. */
 @Transactional
 public void redeem(Quote expected,String email,Cart cart){
  if(!expected.applied()||expected.code()==null)return;
  MarketplaceOffer row=offers.lockByCode(expected.code()).orElseThrow(()->
   new ResponseStatusException(HttpStatus.CONFLICT,"Offer is no longer available"));
  BigDecimal amount=subtotal(cart);
  String issue=ineligible(row,amount,email);
  if(issue!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,issue);
  BigDecimal current=discount(row,amount);
  if(paise(current.doubleValue())!=paise(expected.discount())||
     paise(amount.doubleValue())!=paise(expected.subtotal()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Offer amount changed; review your order");
  row.setUsedCount(row.getUsedCount()+1);
  offers.save(row);
 }
}
