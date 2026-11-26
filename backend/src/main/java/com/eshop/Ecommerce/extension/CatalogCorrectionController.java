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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Admin -> seller -> admin correction review. Existing listing IDs and inventory stay intact. */
@RestController
public class CatalogCorrectionController {
 private final ProductRepo products;
 private final CatalogCorrectionRequestRepository corrections;
 private final AuthUtil auth;
 private final DomainEvents events;
 public CatalogCorrectionController(ProductRepo products,CatalogCorrectionRequestRepository corrections,
   AuthUtil auth,DomainEvents events){
  this.products=products;this.corrections=corrections;this.auth=auth;this.events=events;
 }
 public record Ask(@NotNull Long productId,@NotBlank @Pattern(regexp="IMAGE|DETAILS") String kind,
   @NotBlank @Size(max=500) String message){}
 public record Response(@Size(max=500) String note){}
 public record Review(@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,
   @Size(max=500) String note){}
 public record Link(@NotNull Long productId,@NotNull Long canonicalProductId,
   @NotBlank @Size(min=8,max=500) String reason){}
 public record Correction(Long id,Long productId,Long sellerId,String kind,String status,String message,
   String sellerNote,String reviewNote,String requestedBy,OffsetDateTime requestedAt,
   OffsetDateTime submittedAt,OffsetDateTime reviewedAt){}

 private static Correction view(CatalogCorrectionRequest r){
  return new Correction(r.getId(),r.getProductId(),r.getSellerId(),r.getKind(),
    r.getStatus(),r.getMessage(),r.getSellerNote(),r.getReviewNote(),r.getRequestedBy(),
    r.getRequestedAt(),r.getSubmittedAt(),r.getReviewedAt());
 }
 private static boolean changed(CatalogCorrectionRequest r,Product p){
  if("IMAGE".equals(r.getKind()))
   return p.getImage()!=null&&!p.getImage().isBlank()&&!Objects.equals(r.getOriginalImage(),p.getImage());
  return !Objects.equals(r.getOriginalName(),p.getProductName())||
    !Objects.equals(r.getOriginalDescription(),p.getDescription())||
    !Objects.equals(r.getOriginalBrand(),p.getBrand());
 }

 @PostMapping("/api/admin/catalog-corrections")
 @Transactional
 public Correction ask(@Valid @RequestBody Ask data){
  Product p=products.findByIdForInventoryUpdate(data.productId()).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
  if(p.getUser()==null)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only seller-owned listings can receive a correction request");
  if(!"APPROVED".equals(p.getListingStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Listing must be approved before requesting a correction");
  if("DETAILS".equals(data.kind())&&p.getCatalogProductId()!=null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Matched offers inherit catalog identity; request an image update or correct the canonical product");
  if(corrections.existsByProductIdAndStatusIn(p.getProductId(),List.of("REQUESTED","SUBMITTED")))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"An open correction already exists for this listing");
  CatalogCorrectionRequest r=new CatalogCorrectionRequest();
  r.setProductId(p.getProductId());r.setSellerId(p.getUser().getUserid());
  r.setKind(data.kind());r.setStatus("REQUESTED");r.setMessage(data.message().trim());
  r.setRequestedBy(auth.loggedInUser().getUserName());r.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
  r.setOriginalImage(p.getImage());r.setOriginalDescription(p.getDescription());
  r.setOriginalName(p.getProductName());r.setOriginalBrand(p.getBrand());
  return view(corrections.saveAndFlush(r));
 }

 @GetMapping("/api/seller/catalog-corrections")
 @Transactional(readOnly=true)
 public List<Correction> sellerRequests(){
  return corrections.findBySellerIdOrderByRequestedAtDesc(auth.loggedInUserId()).stream()
    .map(CatalogCorrectionController::view).toList();
 }
 @PostMapping("/api/seller/catalog-corrections/{id}/submit")
 @Transactional
 public Correction submit(@PathVariable Long id,@Valid @RequestBody Response body){
  CatalogCorrectionRequest r=corrections.findById(id).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Correction request not found"));
  if(!r.getSellerId().equals(auth.loggedInUserId()))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Correction request not found");
  if(!"REQUESTED".equals(r.getStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Correction already submitted or closed");
  Product p=products.findByIdForInventoryUpdate(r.getProductId()).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
  if(p.getUser()==null||!r.getSellerId().equals(p.getUser().getUserid()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Listing ownership changed");
  if(!"PENDING".equals(p.getListingStatus())||!changed(r,p))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Use Edit Listing or Upload Photo first; the requested changes must be pending review");
  r.setStatus("SUBMITTED");r.setSellerNote(body.note()==null?null:body.note().trim());
  r.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
  return view(corrections.saveAndFlush(r));
 }

 @GetMapping("/api/admin/catalog-corrections")
 @Transactional(readOnly=true)
 public List<Correction> all(){
  return corrections.findAllByOrderByRequestedAtDesc().stream()
    .map(CatalogCorrectionController::view).toList();
 }
 @PutMapping("/api/admin/catalog-corrections/{id}/review")
 @Transactional
 public Correction review(@PathVariable Long id,@Valid @RequestBody Review body){
  CatalogCorrectionRequest r=corrections.findById(id).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Correction request not found"));
  if(!"SUBMITTED".equals(r.getStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Seller must submit updated listing before review");
  Product p=products.findByIdForInventoryUpdate(r.getProductId()).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Listing not found"));
  if(p.getUser()==null||!r.getSellerId().equals(p.getUser().getUserid())||
     !"PENDING".equals(p.getListingStatus())||!changed(r,p))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Submitted changes no longer match the pending listing");
  p.setListingStatus(body.decision());p.setListingReviewNote(body.note());
  products.save(p);
  events.record("PRODUCT_CHANGED",p.getProductId(),null);
  r.setStatus(body.decision());r.setReviewNote(body.note()==null?null:body.note().trim());
  r.setReviewedBy(auth.loggedInUser().getUserName());
  r.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
  return view(corrections.saveAndFlush(r));
 }

 private static String norm(String s){
  return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
 }

 @PostMapping("/api/admin/catalog-quality/link")
 @Transactional
 public Map<String,Object> link(@Valid @RequestBody Link body){
  if(body.productId().equals(body.canonicalProductId()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select two different products");
  // Deterministic lock ordering avoids deadlocks during concurrent administrator changes.
  Long low=Math.min(body.productId(),body.canonicalProductId());
  Long high=Math.max(body.productId(),body.canonicalProductId());
  Product first=products.findByIdForInventoryUpdate(low).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalog product not found"));
  Product second=products.findByIdForInventoryUpdate(high).orElseThrow(()->
    new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalog product not found"));
  Product source=body.productId().equals(low)?first:second;
  Product canonical=body.canonicalProductId().equals(low)?first:second;
  if(source.getUser()==null||source.getCatalogProductId()!=null||canonical.getCatalogProductId()!=null||
      products.existsByCatalogProductId(source.getProductId()) ||
      source.getCategory()==null||canonical.getCategory()==null ||
      !Objects.equals(source.getCategory().getCategoryId(),canonical.getCategory().getCategoryId()) ||
      !norm(source.getBrand()).equals(norm(canonical.getBrand())) ||
      !norm(source.getProductName()).equals(norm(canonical.getProductName())) ||
      source.getVariantGroup()!=null||canonical.getVariantGroup()!=null||
      source.getSizeType()!=null||canonical.getSizeType()!=null ||
      !"APPROVED".equals(source.getListingStatus())||
      !"APPROVED".equals(canonical.getListingStatus()))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only independent approved listings with identical name, brand and category can be linked");
  Long seller=source.getUser().getUserid();
  if((canonical.getUser()!=null&&seller.equals(canonical.getUser().getUserid()))||
      products.existsByCatalogProductIdAndUser_Userid(canonical.getProductId(),seller))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Seller already offers this catalog product");
  if(corrections.existsByProductIdAndStatusIn(source.getProductId(),List.of("REQUESTED","SUBMITTED")))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Finish outstanding corrections before linking");
  source.setCatalogProductId(canonical.getProductId());
  // Keep the source's product ID, owner, submitted image, selling price and stock.
  products.save(source);
  events.record("PRODUCT_CHANGED",source.getProductId(),null);
  return Map.of("productId",source.getProductId(),
    "catalogProductId",canonical.getProductId(),"linked",true,
    "message","Catalog identity linked without merging product IDs or inventory");
 }
}
