package com.eshop.Ecommerce.extension;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class SearchController {
 private final OpenSearchCatalog search;
 public SearchController(OpenSearchCatalog search){this.search=search;}
 @GetMapping("/api/search/products")
 public JsonNode search(@RequestParam(defaultValue="")String q,@RequestParam(required=false)String category,
 @RequestParam(required=false)Double minPrice,@RequestParam(required=false)Double maxPrice,
 @RequestParam(required=false)String city,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
 return search.search(q,category,minPrice,maxPrice,city,page,size);
 }
 @PostMapping("/api/admin/search/reindex")
 public Map<String,Long> reindex(){return Map.of("indexed",search.reindex());}
}