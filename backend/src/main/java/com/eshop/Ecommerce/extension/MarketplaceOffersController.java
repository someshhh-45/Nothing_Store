package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.CartOfferService;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.Instant;
import java.util.*;

@RestController
public class MarketplaceOffersController {
 private final MarketplaceOfferRepository offers;
 private final CartRepository carts;
 private final CartOfferService pricing;
 private final AuthUtil auth;
 public MarketplaceOffersController(MarketplaceOfferRepository offers,CartRepository carts,
  CartOfferService pricing,AuthUtil auth){
  this.offers=offers;this.carts=carts;this.pricing=pricing;this.auth=auth;
 }
 public record NewOffer(
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,32}") String code,
  @NotBlank @Size(max=90) String title,
  @Size(max=300) String description,
  @NotNull MarketplaceOffer.Kind kind,
  @NotNull @DecimalMin("0.01") BigDecimal value,
  @NotNull @DecimalMin("0.0") BigDecimal minSubtotal,
  @DecimalMin("0.0") BigDecimal maxSubtotal,
  @DecimalMin("0.0") BigDecimal maxDiscount,
  @NotNull Instant startsAt,@NotNull Instant endsAt,
  @NotNull @Min(1) @Max(1000000) Integer globalLimit,
  @NotNull @Min(1) @Max(100) Integer perCustomerLimit,
  boolean autoApply){}
 public record SelectCode(String code){}
 public record ToggleOffer(boolean active){}
 private Map<String,Object> view(MarketplaceOffer v,boolean admin){
  var data=new LinkedHashMap<String,Object>();
  data.put("id",v.getId());data.put("code",v.getCode());data.put("title",v.getTitle());
  data.put("description",v.getDescription());data.put("kind",v.getKind().name());
  data.put("value",v.getValue());data.put("minSubtotal",v.getMinSubtotal());
  data.put("maxSubtotal",v.getMaxSubtotal());data.put("maxDiscount",v.getMaxDiscount());
  data.put("startsAt",v.getStartsAt());data.put("endsAt",v.getEndsAt());
  data.put("autoApply",v.isAutoApply());data.put("active",v.isActive());
  if(admin){
   data.put("usedCount",v.getUsedCount());data.put("globalLimit",v.getGlobalLimit());
   data.put("perCustomerLimit",v.getPerCustomerLimit());
  }
  return data;
 }
 @GetMapping("/api/store/offers")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> available(){
  Instant now=Instant.now();
  return offers.findByActiveTrueOrderByMinSubtotalAsc().stream()
   .filter(v->!now.isBefore(v.getStartsAt())&&now.isBefore(v.getEndsAt()))
   .filter(v->v.getGlobalLimit()==null||v.getUsedCount()<v.getGlobalLimit())
   .map(v->view(v,false)).toList();
 }
 @GetMapping("/api/customer/cart/quote")
 @Transactional(readOnly=true)
 public CartOfferService.Quote quote(){
  return pricing.quote(carts.findCartByEmail(auth.loggedInEmail()),auth.loggedInEmail(),false);
 }
 @PutMapping("/api/customer/cart/coupon")
 @Transactional
 public CartOfferService.Quote apply(@RequestBody SelectCode selected){
  if(selected==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a coupon");
  String code=CartOfferService.normalizeCode(selected.code());
  if(code==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a coupon");
  Cart cart=carts.findCartByEmail(auth.loggedInEmail());
  if(cart==null||cart.getCartItems().isEmpty())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Add products before applying a coupon");
  String previous=cart.getCouponCode();
  cart.setCouponCode(code);
  try {
   var quote=pricing.quote(cart,auth.loggedInEmail(),true);
   carts.save(cart);
   return quote;
  } catch(RuntimeException ex){
   cart.setCouponCode(previous);
   throw ex;
  }
 }
 @DeleteMapping("/api/customer/cart/coupon")
 @Transactional
 public CartOfferService.Quote remove(){
  Cart cart=carts.findCartByEmail(auth.loggedInEmail());
  if(cart==null)return pricing.quote(null,auth.loggedInEmail(),false);
  cart.setCouponCode(null);carts.save(cart);
  return pricing.quote(cart,auth.loggedInEmail(),false);
 }
 @GetMapping("/api/admin/offers")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> all(){return offers.findAllByOrderByIdDesc().stream().map(o->view(o,true)).toList();}
 @PostMapping("/api/admin/offers")
 @ResponseStatus(HttpStatus.CREATED)
 @Transactional
 public Map<String,Object> create(@Valid @RequestBody NewOffer v){
  String code=CartOfferService.normalizeCode(v.code());
  if(offers.findByCodeIgnoreCase(code).isPresent())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This offer code already exists");
  if(v.kind()==MarketplaceOffer.Kind.PERCENT&&v.value().compareTo(new BigDecimal("80"))>0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Percentage cannot exceed 80%");
  if(v.value().compareTo(new BigDecimal("100000"))>0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Offer value is too large");
  if(!v.startsAt().isBefore(v.endsAt())||
    (v.maxSubtotal()!=null&&v.maxSubtotal().compareTo(v.minSubtotal())<0))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid offer dates or cart amount range");
  var row=new MarketplaceOffer();
  row.setCode(code);row.setTitle(v.title().trim());row.setDescription(v.description());
  row.setKind(v.kind());row.setValue(v.value().setScale(2,RoundingMode.HALF_UP));
  row.setMinSubtotal(v.minSubtotal().setScale(2,RoundingMode.HALF_UP));
  row.setMaxSubtotal(v.maxSubtotal()==null?null:v.maxSubtotal().setScale(2,RoundingMode.HALF_UP));
  row.setMaxDiscount(v.maxDiscount()==null?null:v.maxDiscount().setScale(2,RoundingMode.HALF_UP));
  row.setStartsAt(v.startsAt());row.setEndsAt(v.endsAt());row.setGlobalLimit(v.globalLimit());
  row.setPerCustomerLimit(v.perCustomerLimit());row.setAutoApply(v.autoApply());
  return view(offers.saveAndFlush(row),true);
 }
 @PutMapping("/api/admin/offers/{id}/active")
 @Transactional
 public Map<String,Object> activate(@PathVariable Long id,@RequestBody ToggleOffer request){
  MarketplaceOffer v=offers.findById(id).orElseThrow(()->
   new ResponseStatusException(HttpStatus.NOT_FOUND,"Offer not found"));
  v.setActive(request.active());return view(offers.save(v),true);
 }
}
