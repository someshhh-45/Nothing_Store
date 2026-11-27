package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.DeliveryCoverageService;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
public class DeliveryCoverageController {
 private final DeliveryCoverageService delivery;
 private final DeliveryCoverageZoneRepository zones;
 private final ProductRepo products;
 private final CartRepository carts;
 private final AddressRepository addresses;
 private final AuthUtil auth;
 public DeliveryCoverageController(DeliveryCoverageService delivery,DeliveryCoverageZoneRepository zones,
   ProductRepo products,CartRepository carts,AddressRepository addresses,AuthUtil auth){
  this.delivery=delivery;this.zones=zones;this.products=products;
  this.carts=carts;this.addresses=addresses;this.auth=auth;
 }
 public record ZoneInput(@NotBlank @Pattern(regexp="[1-9][0-9]{5}") String pinFrom,
  @NotBlank @Pattern(regexp="[1-9][0-9]{5}") String pinTo,
  @Min(1) @Max(30) int etaMinDays,@Min(1) @Max(45) int etaMaxDays){}
 public record ZoneView(Long id,Long sellerId,String pinFrom,String pinTo,
  int etaMinDays,int etaMaxDays,boolean enabled){}
 private ZoneView view(DeliveryCoverageZone z){
  return new ZoneView(z.getId(),z.getSellerKey()==0?null:z.getSellerKey(),
   z.getPinFrom(),z.getPinTo(),z.getEtaMinDays(),z.getEtaMaxDays(),z.isEnabled());
 }
 private List<ZoneView> list(Long sellerKey){
  return zones.findBySellerKeyOrderByPinFromAsc(sellerKey).stream().map(this::view).toList();
 }
 private ZoneView create(Long sellerKey,ZoneInput input){
  if(input.pinFrom().compareTo(input.pinTo())>0||input.etaMaxDays()<input.etaMinDays())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid PIN range or delivery window");
  if(zones.findBySellerKeyOrderByPinFromAsc(sellerKey).stream()
      .filter(DeliveryCoverageZone::isEnabled)
      .anyMatch(z->z.getPinFrom().compareTo(input.pinTo())<=0&&z.getPinTo().compareTo(input.pinFrom())>=0))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This PIN range overlaps an existing active zone");
  DeliveryCoverageZone zone=new DeliveryCoverageZone();
  zone.setSellerKey(sellerKey);zone.setPinFrom(input.pinFrom());zone.setPinTo(input.pinTo());
  zone.setEtaMinDays(input.etaMinDays());zone.setEtaMaxDays(input.etaMaxDays());
  zone.setEnabled(true);zone.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
  return view(zones.saveAndFlush(zone));
 }
 private void disable(Long sellerKey,Long id){
  DeliveryCoverageZone zone=zones.findById(id).orElseThrow(()->
   new ResponseStatusException(HttpStatus.NOT_FOUND,"Zone not found"));
  if(!sellerKey.equals(zone.getSellerKey()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Zone not found");
  zone.setEnabled(false);zone.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
  zones.save(zone);
 }

 @GetMapping("/api/store/delivery/check")
 @Transactional(readOnly=true)
 public DeliveryCoverageService.Result check(@RequestParam Long productId,@RequestParam String pincode){
  Product product=products.findById(productId).orElseThrow(()->
   new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
  if(!"APPROVED".equals(product.getListingStatus())||product.getQuantity()==null)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not available");
  return delivery.check(product,pincode);
 }
 @GetMapping("/api/customer/delivery/cart")
 @Transactional(readOnly=true)
 public DeliveryCoverageService.CartResult cart(@RequestParam Long addressId){
  Address address=addresses.findById(addressId).orElseThrow(()->
   new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found"));
  if(address.getUser()==null||!auth.loggedInUserId().equals(address.getUser().getUserid()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Address not found");
  return delivery.checkCart(carts.findCartByEmail(auth.loggedInEmail()),address.getPincode());
 }
 @GetMapping("/api/seller/delivery/zones")
 @Transactional(readOnly=true)
 public List<ZoneView> mine(){return list(auth.loggedInUserId());}
 @PostMapping("/api/seller/delivery/zones")
 @ResponseStatus(HttpStatus.CREATED)
 @Transactional
 public ZoneView add(@Valid @RequestBody ZoneInput input){return create(auth.loggedInUserId(),input);}
 @DeleteMapping("/api/seller/delivery/zones/{id}")
 @Transactional
 public void remove(@PathVariable Long id){disable(auth.loggedInUserId(),id);}

 @GetMapping("/api/admin/delivery/zones")
 @Transactional(readOnly=true)
 public List<ZoneView> platform(){return list(0L);}
 @PostMapping("/api/admin/delivery/zones")
 @ResponseStatus(HttpStatus.CREATED)
 @Transactional
 public ZoneView addPlatform(@Valid @RequestBody ZoneInput input){return create(0L,input);}
 @DeleteMapping("/api/admin/delivery/zones/{id}")
 @Transactional
 public void removePlatform(@PathVariable Long id){disable(0L,id);}
}
