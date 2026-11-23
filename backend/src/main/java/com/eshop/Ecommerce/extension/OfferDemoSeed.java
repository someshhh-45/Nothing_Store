package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.MarketplaceOffer;
import com.eshop.Ecommerce.Repositories.MarketplaceOfferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent sample promotions, enabled only when explicitly SEED_OFFERS=true. */
@Component
public class OfferDemoSeed {
 private final MarketplaceOfferRepository offers;
 @Value("${SEED_OFFERS:false}") private boolean enabled;
 public OfferDemoSeed(MarketplaceOfferRepository offers){this.offers=offers;}
 @EventListener(ApplicationReadyEvent.class)
 @Transactional
 public void seed(){
  if(!enabled)return;
  seed("WELCOME100","₹100 off your first ₹999+ basket",
       MarketplaceOffer.Kind.FLAT,"100","999",null,"100",false,1);
  seed("SAVE10","10% off ₹1,999–₹5,999",
       MarketplaceOffer.Kind.PERCENT,"10","1999","5999","400",false,2);
  seed("AUTOSAVE5","Automatic 5% off ₹1,500+",
       MarketplaceOffer.Kind.PERCENT,"5","1500",null,"250",true,10);
 }
 private void seed(String code,String title,MarketplaceOffer.Kind kind,String amount,String min,
   String max,String cap,boolean automatic,int perUser){
  if(offers.findByCodeIgnoreCase(code).isPresent())return;
  MarketplaceOffer v=new MarketplaceOffer();
  v.setCode(code);v.setTitle(title);
  v.setDescription("Demo campaign. Valid on eligible baskets while available.");
  v.setKind(kind);v.setValue(new BigDecimal(amount));v.setMinSubtotal(new BigDecimal(min));
  if(max!=null)v.setMaxSubtotal(new BigDecimal(max));
  v.setMaxDiscount(new BigDecimal(cap));
  v.setStartsAt(Instant.now().minus(1,ChronoUnit.DAYS));
  v.setEndsAt(Instant.now().plus(180,ChronoUnit.DAYS));
  v.setGlobalLimit(10000);v.setPerCustomerLimit(perUser);
  v.setAutoApply(automatic);
  offers.save(v);
 }
}
