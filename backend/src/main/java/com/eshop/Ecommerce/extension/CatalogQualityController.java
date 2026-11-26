package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.CatalogQualityReview;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CatalogQualityReviewRepository;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only catalog audit with separate review annotations.
 * No product IDs, cart items, images, stock, orders or seller ownership are changed.
 */
@RestController
@RequestMapping("/api/admin/catalog-quality")
public class CatalogQualityController {
 private final ProductRepo products;
 private final CatalogQualityReviewRepository reviews;
 private final AuthUtil auth;

 public CatalogQualityController(ProductRepo products,CatalogQualityReviewRepository reviews,AuthUtil auth){
  this.products=products;this.reviews=reviews;this.auth=auth;
 }

 public record Listing(Long productId,String name,String brand,String category,Long sellerId,
   Long catalogProductId,String variantGroup,String image,String status,Integer stock){}
 public record Issue(String key,String type,String title,String explanation,List<Listing> listings,
   String reviewStatus,String reviewNote,String reviewedBy,OffsetDateTime reviewedAt){}
 public record Report(long totalListings,int likelyDuplicates,int reusedImages,int missingSellerImages,
   int reviewed,int dismissed,List<Issue> issues){}
 public record Decision(@NotBlank @Pattern(regexp="REVIEWED|DISMISSED|OPEN") String status,
   @Size(max=500) String note){}

 private static String norm(String s){
  return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
 }
 private static String imageKey(String s){
  if(s==null||s.isBlank())return "";
  String plain=s.trim().split("[?#]",2)[0];
  return plain.toLowerCase(Locale.ROOT);
 }
 private static String canonicalGroup(Product p){
  if(p.getCatalogProductId()!=null)return "catalog:"+p.getCatalogProductId();
  if(p.getVariantGroup()!=null&&!p.getVariantGroup().isBlank())
    return "variant:"+norm(p.getVariantGroup())+":"+norm(p.getBrand());
  // A canonical row and seller offers linked to it represent one catalog item.
  return "catalog:"+p.getProductId();
 }
 private static String issueKey(String type,List<Product> rows){
  String data=type+":"+rows.stream().map(p->p.getProductId().toString()).sorted().collect(Collectors.joining(","));
  try{
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
   return type.toLowerCase(Locale.ROOT)+"-"+HexFormat.of().formatHex(bytes,0,16);
  }catch(Exception ex){throw new IllegalStateException("Catalog audit cannot generate issue identifiers",ex);}
 }
 private static Listing listing(Product p){
  return new Listing(p.getProductId(),p.getProductName(),p.getBrand(),
    p.getCategory()==null?"Other":p.getCategory().getCategoryName(),
    p.getUser()==null?null:p.getUser().getUserid(),p.getCatalogProductId(),
    p.getVariantGroup(),p.getImage(),p.getListingStatus(),p.getQuantity());
 }
 private static Issue issue(String type,String title,String explanation,List<Product> products){
  var ordered=products.stream().sorted(Comparator.comparing(Product::getProductId)).toList();
  return new Issue(issueKey(type,ordered),type,title,explanation,
    ordered.stream().map(CatalogQualityController::listing).toList(),
    "OPEN",null,null,null);
 }

 /**
  * Candidate detection is deliberately conservative: identical normalized title,
  * brand, and category across INDEPENDENT catalog groups, not seller offers
  * of the same catalog product and not separate sizes of one design.
  */
 static List<Issue> detect(Collection<Product> products){
  List<Product> rows=products.stream()
    .filter(p->p.getProductId()!=null&&p.getProductName()!=null)
    .sorted(Comparator.comparing(Product::getProductId)).toList();
  Map<String,List<Product>> sameIdentity=new TreeMap<>(),sameImage=new TreeMap<>();
  for(Product p:rows){
   String category=p.getCategory()==null?"":norm(p.getCategory().getCategoryName());
   String identity=category+"|"+norm(p.getBrand())+"|"+
      norm(p.getVariantGroup()!=null&&!p.getVariantGroup().isBlank()?p.getVariantGroup():p.getProductName());
   sameIdentity.computeIfAbsent(identity,k->new ArrayList<>()).add(p);
   String photo=imageKey(p.getImage());
   if(!photo.isBlank())sameImage.computeIfAbsent(photo,k->new ArrayList<>()).add(p);
  }
  List<Issue> issues=new ArrayList<>();
  for(var entry:sameIdentity.entrySet()){
   List<Product> uniqueGroups=distinctCatalogGroups(entry.getValue());
   if(uniqueGroups.size()>1)
    issues.add(issue("POSSIBLE_DUPLICATE","Possible duplicate catalog products",
     "Different independent catalog items have the same normalized name, brand and category. Verify identity before making changes.",uniqueGroups));
  }
  for(var entry:sameImage.entrySet()){
   List<Product> uniqueGroups=distinctCatalogGroups(entry.getValue());
   if(uniqueGroups.size()>1)
    issues.add(issue("SHARED_IMAGE","Different products reuse one image",
     "An identical image URL is used by distinct catalog products. Review photographs; legitimate multi-seller offers and size variants are excluded.",uniqueGroups));
  }
  for(Product p:rows){
   if(p.getUser()!=null&&imageKey(p.getImage()).isBlank()&&p.getCatalogProductId()==null)
    issues.add(issue("MISSING_IMAGE","Seller listing has no product photograph",
     "Ask the seller for an accurate photo. This review never automatically changes the listing.",List.of(p)));
  }
  issues.sort(Comparator.comparing(Issue::type).thenComparing(Issue::key));
  return List.copyOf(issues);
 }
 private static List<Product> distinctCatalogGroups(List<Product> rows){
  Map<String,Product> unique=new LinkedHashMap<>();
  for(Product p:rows)unique.putIfAbsent(canonicalGroup(p),p);
  return new ArrayList<>(unique.values());
 }

 @GetMapping
 @Transactional(readOnly=true)
 public Report report(){
  List<Product> catalog=products.findAll();
  List<Issue> candidates=detect(catalog);
  var notes=reviews.findAll().stream().collect(Collectors.toMap(CatalogQualityReview::getIssueKey,r->r));
  List<Issue> output=new ArrayList<>();
  for(Issue i:candidates){
   CatalogQualityReview decision=notes.get(i.key());
   output.add(new Issue(i.key(),i.type(),i.title(),i.explanation(),i.listings(),
      decision==null?"OPEN":decision.getStatus(),
      decision==null?null:decision.getNote(),
      decision==null?null:decision.getReviewedBy(),
      decision==null?null:decision.getUpdatedAt()));
  }
  return new Report(catalog.size(),
   (int)output.stream().filter(i->i.type().equals("POSSIBLE_DUPLICATE")).count(),
   (int)output.stream().filter(i->i.type().equals("SHARED_IMAGE")).count(),
   (int)output.stream().filter(i->i.type().equals("MISSING_IMAGE")).count(),
   (int)output.stream().filter(i->i.reviewStatus().equals("REVIEWED")).count(),
   (int)output.stream().filter(i->i.reviewStatus().equals("DISMISSED")).count(),
   output);
 }

 @PutMapping("/{key}/review")
 @Transactional
 public Map<String,Object> review(@PathVariable String key,@Valid @RequestBody Decision decision){
  if(!key.matches("[a-z_]+-[a-f0-9]{32}"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid issue key");
  if(detect(products.findAll()).stream().noneMatch(i->i.key().equals(key)))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalog issue no longer exists; refresh the audit");
  if("OPEN".equals(decision.status())){
   reviews.deleteById(key);
   return Map.of("key",key,"status","OPEN");
  }
  CatalogQualityReview entry=reviews.findById(key).orElseGet(CatalogQualityReview::new);
  entry.setIssueKey(key);entry.setStatus(decision.status());
  entry.setNote(decision.note()==null?null:decision.note().trim());
  entry.setReviewedBy(auth.loggedInUser().getUserName());
  entry.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
  reviews.saveAndFlush(entry);
  return Map.of("key",key,"status",entry.getStatus());
 }
}
