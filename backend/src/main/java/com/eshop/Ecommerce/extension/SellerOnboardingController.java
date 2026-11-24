package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController
public class SellerOnboardingController {
 private final SellerProfileRepository profiles;
 private final ProductRepo products;
 private final RoleRepository roles;
 private final UserRepository users;
 private final AuthUtil auth;
 private final DomainEvents events;

 public SellerOnboardingController(SellerProfileRepository profiles,ProductRepo products,
    RoleRepository roles,UserRepository users,AuthUtil auth,DomainEvents events){
  this.profiles=profiles;this.products=products;this.roles=roles;this.users=users;this.auth=auth;this.events=events;
 }
 public record ApplyRequest(@NotBlank @Size(min=3,max=120) String businessName,
   @NotBlank @Pattern(regexp="[6-9][0-9]{9}") String contactPhone,
   @Pattern(regexp="|(?:0[1-9]|[12][0-9]|3[0-8])[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]")
   String gstin){}
 public record ReviewRequest(String decision,String note){}
 private static Map<String,Object> view(SellerProfile p){
  Map<String,Object> data=new LinkedHashMap<>();
  data.put("id",p.getId());data.put("businessName",p.getBusinessName());
  data.put("contactPhone",p.getContactPhone());data.put("gstin",p.getGstin());
  data.put("status",p.getStatus());data.put("submittedAt",p.getSubmittedAt());
  data.put("reviewNote",p.getReviewNote());data.put("reviewedAt",p.getReviewedAt());
  return data;
 }
 @GetMapping("/api/seller-applications/me")
 @Transactional(readOnly=true)
 public Map<String,Object> me(){
  return profiles.findByUser_Userid(auth.loggedInUserId()).map(SellerOnboardingController::view)
     .orElse(Map.of("status","NOT_APPLIED"));
 }
 @PostMapping("/api/seller-applications")
 @Transactional
 public Map<String,Object> apply(@Valid @RequestBody ApplyRequest request){
  User user=auth.loggedInUser();
  SellerProfile p=profiles.findByUser_Userid(user.getUserid()).orElse(null);
  if(p!=null&&Set.of("PENDING","APPROVED").contains(p.getStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Seller application already submitted or approved");
  if(p==null){p=new SellerProfile();p.setUser(user);}
  p.setBusinessName(request.businessName().trim());
  p.setContactPhone(request.contactPhone().trim());
  p.setGstin(request.gstin()==null||request.gstin().isBlank()?null:request.gstin().toUpperCase(Locale.ROOT));
  p.setStatus("PENDING");p.setSubmittedAt(Instant.now());
  p.setReviewedAt(null);p.setReviewedBy(null);p.setReviewNote(null);
  return view(profiles.save(p));
 }
 @GetMapping("/api/admin/seller-applications")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> pending(){
  return profiles.findByStatusOrderBySubmittedAtAsc("PENDING").stream().map(SellerOnboardingController::view).toList();
 }
 @PutMapping("/api/admin/seller-applications/{id}/review")
 @Transactional
 public Map<String,Object> review(@PathVariable Long id,@RequestBody ReviewRequest decision){
  String status=decision.decision()==null?"":decision.decision().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("APPROVED","REJECTED").contains(status))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision must be APPROVED or REJECTED");
  if(decision.note()!=null&&decision.note().length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Review note is too long");
  SellerProfile p=profiles.findById(id)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Seller application not found"));
  if(!"PENDING".equals(p.getStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Application has already been reviewed");
  p.setStatus(status);p.setReviewNote(decision.note());p.setReviewedAt(Instant.now());
  p.setReviewedBy(auth.loggedInUser().getUserName());
  if("APPROVED".equals(status)){
   User seller=p.getUser();
   Role role=roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow();
   seller.getRoles().add(role);
   users.save(seller);
  }
  return view(profiles.save(p));
 }
 @GetMapping("/api/admin/seller-listings")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> pendingListings(){
  return products.findByListingStatusOrderByProductIdAsc("PENDING").stream()
    .map(p->{
      Map<String,Object> item=new LinkedHashMap<>();
      item.put("productId",p.getProductId());item.put("productName",p.getProductName());
      item.put("seller",p.getUser()==null?"Unknown":p.getUser().getUserName());
      item.put("brand",p.getBrand());item.put("price",p.getSpecialPrice());
      item.put("quantity",p.getQuantity());item.put("status",p.getListingStatus());
      item.put("description",p.getDescription());item.put("image",p.getImage());
      item.put("category",p.getCategory()==null?"Other":p.getCategory().getCategoryName());
      return item;
    }).toList();
 }
 @PutMapping("/api/admin/seller-listings/{id}/review")
 @Transactional
 public Map<String,Object> reviewListing(@PathVariable Long id,@RequestBody ReviewRequest decision){
  String status=decision.decision()==null?"":decision.decision().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("APPROVED","REJECTED").contains(status))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision must be APPROVED or REJECTED");
  if(decision.note()!=null&&decision.note().length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Review note is too long");
  Product p=products.findByIdForInventoryUpdate(id)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Listing not found"));
  if(!"PENDING".equals(p.getListingStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only pending listings may be reviewed");
  p.setListingStatus(status);p.setListingReviewNote(decision.note());
  products.save(p);
  events.record("PRODUCT_CHANGED",p.getProductId(),null);
  return Map.of("productId",p.getProductId(),"status",status);
 }
}
