package com.eshop.Ecommerce.Service;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Never infer deliverability from seller dispatch city. Postal coverage is explicit. */
@Service
public class DeliveryCoverageService {
 private final DeliveryCoverageZoneRepository zones;
 private final boolean requireConfigured;
 public DeliveryCoverageService(DeliveryCoverageZoneRepository zones,
    @Value("${nothing.delivery.require-configured:false}") boolean requireConfigured){
  this.zones=zones;this.requireConfigured=requireConfigured;
 }
 public record Result(Long productId,Long sellerId,String pincode,String status,
   boolean checkoutAllowed,Integer minDays,Integer maxDays,String message){}
 public record CartResult(String pincode,boolean checkoutAllowed,
   boolean allVerified,List<Result> products){}

 public static boolean validPin(String pin){return pin!=null&&pin.matches("[1-9][0-9]{5}");}
 public Result check(Product product,String pincode){
  if(!validPin(pincode))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 6-digit Indian PIN");
  Long sellerId=product.getUser()==null?null:product.getUser().getUserid();
  Long key=sellerId==null?0L:sellerId;
  List<DeliveryCoverageZone> active=zones.findBySellerKeyOrderByPinFromAsc(key).stream()
   .filter(DeliveryCoverageZone::isEnabled).toList();
  // No optimistic claims about courier services or ETA when zones are unconfigured.
  if(active.isEmpty())
   return new Result(product.getProductId(),sellerId,pincode,"UNVERIFIED",!requireConfigured,
     null,null,"Seller has not configured a delivery network for this PIN");
  var selected=active.stream()
   .filter(z->z.getPinFrom().compareTo(pincode)<=0 && z.getPinTo().compareTo(pincode)>=0)
   .min(Comparator.comparingInt(DeliveryCoverageZone::getEtaMaxDays)).orElse(null);
  if(selected==null)
   return new Result(product.getProductId(),sellerId,pincode,"UNAVAILABLE",false,
     null,null,"Seller does not serve this PIN");
  return new Result(product.getProductId(),sellerId,pincode,"SERVICEABLE",true,
     selected.getEtaMinDays(),selected.getEtaMaxDays(),
     "Seller-configured delivery window; not a live courier promise");
 }
 public CartResult checkCart(Cart cart,String pin){
  if(!validPin(pin))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 6-digit Indian PIN");
  if(cart==null||cart.getCartItems()==null||cart.getCartItems().isEmpty())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Shopping cart is empty");
  List<Result> checks=cart.getCartItems().stream().map(i->check(i.getProduct(),pin)).toList();
  return new CartResult(pin,checks.stream().allMatch(Result::checkoutAllowed),
    checks.stream().allMatch(r->"SERVICEABLE".equals(r.status())),checks);
 }
 public void requireCheckout(Cart cart,String pin){
  CartResult result=checkCart(cart,pin);
  if(!result.checkoutAllowed()){
   String denied=result.products().stream().filter(r->!r.checkoutAllowed())
    .map(r->"Product #"+r.productId()+": "+r.message()).findFirst().orElse("Delivery not available");
   throw new ResponseStatusException(HttpStatus.CONFLICT,denied);
  }
 }
 public boolean strictMode(){return requireConfigured;}
}
