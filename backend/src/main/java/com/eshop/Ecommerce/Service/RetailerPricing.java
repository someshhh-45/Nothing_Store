package com.eshop.Ecommerce.Service;
import com.eshop.Ecommerce.Repositories.RetailerProfileRepository;
import com.eshop.Ecommerce.Model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class RetailerPricing {
 private final RetailerProfileRepository retailers;
 private final double discountFraction;
 public RetailerPricing(RetailerProfileRepository retailers,
    @Value("${nothing.retailer.discount-percent:10}") double percent){
   this.retailers=retailers;
   this.discountFraction=Math.min(0.50,Math.max(0,percent/100.0));
 }
 public boolean approved(User user){
   return user!=null && retailers.findByUser_Userid(user.getUserid())
     .map(p->"APPROVED".equals(p.getStatus())).orElse(false);
 }
 public int percent(){return (int)Math.round(discountFraction*100);}
 public double customerPrice(User user,double standardPrice){
   if(!approved(user))return standardPrice;
   return java.math.BigDecimal.valueOf(standardPrice*(1-discountFraction))
      .setScale(2,java.math.RoundingMode.HALF_UP).doubleValue();
 }
}
