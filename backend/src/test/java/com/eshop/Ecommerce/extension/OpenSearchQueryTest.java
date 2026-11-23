package com.eshop.Ecommerce.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpenSearchQueryTest {
 private final ObjectMapper mapper=new ObjectMapper();

 @Test
 void substringAnalyzerIndexesInternalPiecesOfEarbuds(){
  Map<String,Object> mapping=OpenSearchCatalog.indexDefinition();
  String json=toJson(mapping);
  assertTrue(json.contains("ngram"));
  assertTrue(json.contains("min_gram"));
  assertTrue(json.contains("max_ngram_diff"));
  assertTrue(json.contains("product_substring"));
  assertTrue(json.contains("search_analyzer"));
 }

 @Test
 void budsQueryUsesNgramFieldInAdditionToFuzzyMatch(){
  String json=toJson(OpenSearchCatalog.searchRequest("buds","Electronics",0.0,3000.0,"Bengaluru",0,50));
  assertTrue(json.contains("\"searchText\""),json);
  assertTrue(json.contains("\"multi_match\""),json);
  assertTrue(json.contains("\"match_phrase_prefix\""),json);
  assertTrue(json.contains("\"category\""),json);
  assertTrue(json.contains("\"city\""),json);
  assertTrue(json.contains("\"price\""),json);
  assertTrue(json.contains("\"buds\""),json);
  assertTrue(json.contains("\"minimum_should_match\":1"),json);
 }

 @Test
 void emptyQueryStillUsesOpenSearchForFacets(){
  String json=toJson(OpenSearchCatalog.searchRequest("","Fashion",null,500.0,null,0,20));
  assertTrue(json.contains("\"match_all\""));
  assertTrue(json.contains("\"Fashion\""));
 }

 @Test
 void invalidPriceRangeIsRejected(){
  assertThrows(IllegalArgumentException.class,
    ()->OpenSearchCatalog.searchRequest("buds",null,1000.0,100.0,null,0,20));
 }

 @Test
 void splitBluetoothMisspellingAddsACompactFuzzyClause(){
  String json=toJson(OpenSearchCatalog.searchRequest("blue teuth",null,null,null,null,0,100));
  assertEquals("blueteuth",OpenSearchCatalog.compactMisspellingCandidate("blue teuth"));
  assertTrue(json.contains("blueteuth"),json);
  assertTrue(json.contains("fuzziness"),json);
  assertTrue(json.contains("minimum_should_match"),json);
  assertNull(OpenSearchCatalog.compactMisspellingCandidate("blue"));
  assertNull(OpenSearchCatalog.compactMisspellingCandidate("laptop gaming keyboard headphones"));
 }

 private String toJson(Object value){
  try{return mapper.writeValueAsString(value);}
  catch(Exception ex){throw new AssertionError(ex);}
 }
}
