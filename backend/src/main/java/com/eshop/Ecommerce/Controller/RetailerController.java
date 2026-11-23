package com.eshop.Ecommerce.Controller;

import com.eshop.Ecommerce.Model.RetailerProfile;
import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Repositories.RetailerProfileRepository;
import com.eshop.Ecommerce.Service.RetailerPricing;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@RestController
public class RetailerController {
 private final RetailerProfileRepository profiles;
 private final AuthUtil auth;
 private final RetailerPricing pricing;
 public RetailerController(RetailerProfileRepository profiles,AuthUtil auth,RetailerPricing pricing){
  this.profiles=profiles;this.auth=auth;this.pricing=pricing;
 }
 public record ApplyRequest(@NotBlank @Size(min=3,max=100) String businessName,
                            @NotBlank String gstin){}
 public record ReviewRequest(String decision,String note){}
 private static final String GSTIN_PATTERN=
  "^(0[1-9]|[12][0-9]|3[0-8])[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
 private Map<String,Object> view(RetailerProfile p){
  Map<String,Object> info=new LinkedHashMap<>();
  info.put("id",p.getId());info.put("businessName",p.getBusinessName());
  info.put("gstin",p.getGstin());info.put("status",p.getStatus());
  info.put("reviewNote",p.getReviewNote());info.put("submittedAt",p.getSubmittedAt());
  info.put("discountPercent","APPROVED".equals(p.getStatus())?pricing.percent():0);
  return info;
 }
 @PostMapping("/api/retailer/apply")
 @Transactional
 public Map<String,Object> apply(@Valid @RequestBody ApplyRequest request){
  User user=auth.loggedInUser();
  String gstin=request.gstin().trim().toUpperCase(Locale.ROOT);
  if(!gstin.matches(GSTIN_PATTERN))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid 15-character GSTIN format");
  String name=request.businessName().trim();
  RetailerProfile p=profiles.findByUser_Userid(user.getUserid()).orElse(null);
  if(p!=null && ("PENDING".equals(p.getStatus())||"APPROVED".equals(p.getStatus())))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already pending or approved");
  if(profiles.existsByGstinIgnoreCase(gstin) && (p==null||!gstin.equalsIgnoreCase(p.getGstin())))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"GSTIN already registered");
  if(p==null){p=new RetailerProfile();p.setUser(user);}
  p.setBusinessName(name);p.setGstin(gstin);p.setStatus("PENDING");p.setReviewNote(null);
  p.setSubmittedAt(Instant.now());p.setReviewedAt(null);p.setReviewedBy(null);
  return view(profiles.save(p));
 }
 @GetMapping("/api/retailer/me")
 @Transactional(readOnly=true)
 public Map<String,Object> me(){
  User user=auth.loggedInUser();
  return profiles.findByUser_Userid(user.getUserid()).map(this::view).orElse(Map.of(
    "status","NOT_APPLIED","discountPercent",0));
 }
 @GetMapping("/api/admin/retailers")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> pending(){
  return profiles.findByStatusOrderBySubmittedAtAsc("PENDING").stream().map(this::view).toList();
 }
 @PutMapping("/api/admin/retailers/{id}/review")
 @Transactional
 public Map<String,Object> review(@PathVariable Long id,@RequestBody ReviewRequest req){
  String decision=req.decision()==null?"":req.decision().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("APPROVED","REJECTED").contains(decision))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decision must be APPROVED or REJECTED");
  RetailerProfile p=profiles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!"PENDING".equals(p.getStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only pending applications can be reviewed");
  if(req.note()!=null && req.note().length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Review note too long");
  p.setStatus(decision);p.setReviewNote(req.note());p.setReviewedAt(Instant.now());
  p.setReviewedBy(auth.loggedInUser().getUserName());
  return view(profiles.save(p));
 }
}
