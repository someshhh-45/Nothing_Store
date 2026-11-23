package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.extension.RetailCatalogSeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExpandedCatalogSeedTest {
 @Autowired RetailCatalogSeed catalog;
 @Autowired ProductRepo products;

 @Test
 void additionalProductsSeedIdempotentlyAndPreserveExistingRecords(){
  ReflectionTestUtils.setField(catalog,"enabled",true);
  catalog.seed();
  var first=products.findAll();
  assertTrue(first.stream().anyMatch(p->"Portable SSD 512GB".equals(p.getProductName())));
  assertTrue(first.stream().anyMatch(p->"Daily Body Lotion 400ml".equals(p.getProductName())));
  assertTrue(first.stream().anyMatch(p->"Arabica Coffee Beans 250g".equals(p.getProductName())));
  assertTrue(first.size()>=46,"The 14 original and 32 additional products should be present");
  // Each seeded photo URL is unique; avoid repeating a generic photo on many cards.
  var photos=first.stream().filter(p->p.getImage()!=null&&!p.getImage().isBlank())
    .filter(p->p.getProductName().equals("Stackable Storage Boxes")
      ||p.getProductName().equals("Aloe Vera Moisturizer 100ml")
      ||p.getProductName().equals("Portable SSD 512GB")
      ||p.getProductName().equals("Ceramic Coffee Mug Set"))
    .map(p->p.getImage()).toList();
  assertEquals(4,photos.size(),"Curated items must have photos");
  assertEquals(4,new java.util.HashSet<>(photos).size(),"Different products need distinct photos");
  long count=first.size();
  catalog.seed();
  assertEquals(count,products.count(),"Restarting must not duplicate demo products");
 }
}
