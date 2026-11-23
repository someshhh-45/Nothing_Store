package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenSearch is the authoritative search backend; PostgreSQL remains the
 * authoritative inventory database. v2 uses character n-grams to find "buds"
 * inside "earbuds" without sacrificing regular typo-tolerant full-word search.
 */
@Service
public class OpenSearchCatalog {
 private static final Logger log=LoggerFactory.getLogger(OpenSearchCatalog.class);
 static final String INDEX="/nothing-products-v2";
 private final ProductRepo products;
 private final ObjectMapper mapper;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final String url,user,password;
 private final AtomicBoolean bootstrapped=new AtomicBoolean(false);
 private final Object bootstrapLock=new Object();

 public OpenSearchCatalog(ProductRepo products,ObjectMapper mapper,
   @Value("${nothing.search.url}")String url,
   @Value("${nothing.search.username:}")String user,
   @Value("${nothing.search.password:}")String password){
  this.products=products;this.mapper=mapper;
  this.url=url.replaceAll("/$","");this.user=user;this.password=password;
 }
 private JsonNode call(String method,String path,Object payload){
  try{
   var builder=HttpRequest.newBuilder(URI.create(url+path))
      .timeout(Duration.ofSeconds(25));
   if(payload!=null)builder.header("Content-Type","application/json");
   if(!user.isBlank())builder.header("Authorization","Basic "+
     Base64.getEncoder().encodeToString((user+":"+password).getBytes(StandardCharsets.UTF_8)));
   builder.method(method,payload==null?HttpRequest.BodyPublishers.noBody():
      HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
   var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()>=300){
     throw new SearchHttpException(response.statusCode(),"OpenSearch HTTP "+response.statusCode()+": "+response.body());
   }
   return response.body().isBlank()?mapper.createObjectNode():mapper.readTree(response.body());
  }catch(SearchHttpException e){throw e;}
   catch(Exception e){throw new IllegalStateException("OpenSearch unavailable",e);}
 }
 private static final class SearchHttpException extends IllegalStateException{
  final int status;
  SearchHttpException(int status,String message){super(message);this.status=status;}
 }

 static Map<String,Object> indexDefinition(){
  Map<String,Object> tokenizer=Map.of("type","ngram","min_gram",3,"max_gram",25,
     "token_chars",List.of("letter","digit"));
  Map<String,Object> analyzer=Map.of("type","custom","tokenizer","product_ngram_tokenizer",
     "filter",List.of("lowercase"));
  Map<String,Object> settings=Map.of("index",Map.of("max_ngram_diff",22),
     "analysis",Map.of("tokenizer",Map.of("product_ngram_tokenizer",tokenizer),
                        "analyzer",Map.of("product_substring",analyzer)));
  Map<String,Object> fields=new LinkedHashMap<>();
  fields.put("productId",Map.of("type","long"));
  fields.put("productName",Map.of("type","text"));
  fields.put("description",Map.of("type","text"));
  fields.put("searchText",Map.of("type","text","analyzer","product_substring","search_analyzer","standard"));
  fields.put("category",Map.of("type","keyword"));
  fields.put("brand",Map.of("type","keyword"));
  fields.put("variantGroup",Map.of("type","keyword"));
  fields.put("variantLabel",Map.of("type","keyword"));
  fields.put("city",Map.of("type","keyword"));
  fields.put("price",Map.of("type","double"));
  fields.put("quantity",Map.of("type","integer"));
  fields.put("image",Map.of("type","keyword","ignore_above",2048));
  return Map.of("settings",settings,"mappings",Map.of("properties",fields));
 }
 public void ensureIndex(){
  try{call("HEAD",INDEX,null);return;}
  catch(SearchHttpException e){if(e.status!=404)throw e;}
  try{call("PUT",INDEX,indexDefinition());}
  catch(SearchHttpException e){
   if(e.status!=400||!e.getMessage().contains("resource_already_exists_exception"))throw e;
  }
 }

 private void writeDocument(Product product){
  Map<String,Object> doc=new LinkedHashMap<>();
  doc.put("productId",product.getProductId());
  doc.put("productName",product.getProductName());
  doc.put("description",product.getDescription());
  doc.put("searchText",(product.getProductName()==null?"":product.getProductName())+" "+
       (product.getDescription()==null?"":product.getDescription())+" "+
       (product.getBrand()==null?"":product.getBrand()));
  doc.put("category",product.getCategory()==null?"":product.getCategory().getCategoryName());
  doc.put("brand",product.getBrand()==null?"NOTHING SELECT":product.getBrand());
  doc.put("variantGroup",product.getVariantGroup());
  doc.put("variantLabel",product.getVariantLabel());
  doc.put("city",product.getCity()==null?"":product.getCity());
  doc.put("price",product.getSpecialPrice());
  doc.put("quantity",product.getQuantity());
  doc.put("image",product.getImage());
  call("PUT",INDEX+"/_doc/"+product.getProductId(),doc);
 }

 public void index(long productId){
  ensureIndex();
  products.findById(productId).ifPresentOrElse(this::writeDocument,()->delete(productId));
 }
 public void delete(long productId){
  try{call("DELETE",INDEX+"/_doc/"+productId,null);}
  catch(SearchHttpException e){if(e.status!=404)throw e;}
 }

 /**
  * New deployments, existing local Docker volumes and existing Aiven clusters
  * all begin with a different index: populate it from PostgreSQL on the first
  * search. Kafka keeps changes in sync afterwards.
  */
 private void initializeSearch(){
  if(bootstrapped.get())return;
  synchronized(bootstrapLock){
   if(bootstrapped.get())return;
   long count=reindex();
   bootstrapped.set(true);
   log.info("OpenSearch {} populated with {} PostgreSQL products",INDEX,count);
  }
 }

 /**
  * Join only short, plain alphabetic fragments; never compact long natural
  * language queries or symbols into an unrelated broad fuzzy term.
  */
 static String compactMisspellingCandidate(String raw){
   if(raw==null)return null;
   String[] tokens=raw.trim().split("\\s+");
   if(tokens.length<2||tokens.length>3)return null;
   int length=0;
   for(String token:tokens){
     if(token.length()<2||!token.matches("[\\p{L}\\p{N}]+"))return null;
     length+=token.length();
   }
   return length>=6&&length<=24 ? String.join("",tokens) : null;
 }

 static Map<String,Object> searchRequest(String q,String category,Double min,Double max,
                                         String city,int page,int size){
  if(page<0||size<1||size>100||(long)page*size>10000)
   throw new IllegalArgumentException("Invalid search pagination");
  if(min!=null&&max!=null&&min>max)
   throw new IllegalArgumentException("Minimum price exceeds maximum price");

  List<Object> filters=new ArrayList<>();
  if(category!=null&&!category.isBlank())filters.add(Map.of("term",Map.of("category",category.trim())));
  if(city!=null&&!city.isBlank())filters.add(Map.of("term",Map.of("city",city.trim())));
  Map<String,Object> range=new LinkedHashMap<>();
  if(min!=null)range.put("gte",min);
  if(max!=null)range.put("lte",max);
  if(!range.isEmpty())filters.add(Map.of("range",Map.of("price",range)));

  String query=q==null?"":q.trim();
  Object must;
  if(query.isBlank())must=Map.of("match_all",Map.of());
  else{
   // Exact/full-word, typo-tolerant, prefix and anywhere-in-word matches.
   // "buds" -> "earbuds"; "bluetoth" -> "bluetooth".
   List<Object> should=new ArrayList<>();
   should.add(Map.of("multi_match",Map.of("query",query,
       "fields",List.of("productName^7","description^2"),
       "fuzziness","AUTO","type","best_fields")));
   should.add(Map.of("match",Map.of("searchText",Map.of("query",query,
       "operator","and","boost",3))));
   should.add(Map.of("match_phrase_prefix",Map.of("productName",Map.of("query",query,"boost",4))));
   // Speech-to-text and casual typing frequently split one product term:
   // "blue teuth" -> "blueteuth", which is two edits from "bluetooth".
   // Preserve the original query AND try the compact form against OpenSearch.
   // This is still a real fuzzy OpenSearch query, not hard-coded SQL fallback.
   String compact=compactMisspellingCandidate(query);
   if(compact!=null){
     should.add(Map.of("multi_match",Map.of(
       "query",compact,"fields",List.of("productName^8","description^2"),
       "fuzziness",2,"prefix_length",1,"max_expansions",100,"type","best_fields")));
   }
   must=Map.of("bool",Map.of("should",should,"minimum_should_match",1));
  }
  return Map.of("from",page*size,"size",size,"track_total_hits",true,
     "query",Map.of("bool",Map.of("must",List.of(must),"filter",filters)));
 }

 public JsonNode search(String q,String category,Double min,Double max,String city,int page,int size){
  var body=searchRequest(q,category,min,max,city,page,size);
  initializeSearch();
  return call("POST",INDEX+"/_search",body);
 }

 public long reindex(){
  ensureIndex();
  long count=0;
  for(Product p:products.findAll()){writeDocument(p);count++;}
  call("POST",INDEX+"/_refresh",null);
  return count;
 }
}
