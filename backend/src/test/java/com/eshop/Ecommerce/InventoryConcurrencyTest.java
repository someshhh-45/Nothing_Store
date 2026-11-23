package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryConcurrencyTest {
 @Autowired private ProductRepo products;
 @Autowired private CategoryRepo categories;
 @Autowired private TransactionTemplate transactions;

 @Test
 void onlyOneBuyerCanReserveTheLastItem() throws Exception {
  Category category=new Category();category.setCategoryName("Concurrency Test");
  category=categories.save(category);
  Product p=new Product();
  p.setProductName("Last Coffee Can");
  p.setDescription("Test product with one remaining unit");
  p.setQuantity(1);p.setPrice(100);p.setSpecialPrice(100);p.setCategory(category);
  long id=products.saveAndFlush(p).getProductId();
  ExecutorService pool=Executors.newFixedThreadPool(2);
  CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
  Callable<Integer> purchase=()->{
   ready.countDown();
   if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timed out");
   return transactions.execute(status->products.reserveStock(id,1));
  };
  try{
   Future<Integer> a=pool.submit(purchase);
   Future<Integer> b=pool.submit(purchase);
   assertTrue(ready.await(10,TimeUnit.SECONDS));
   start.countDown();
   assertEquals(1,a.get(20,TimeUnit.SECONDS)+b.get(20,TimeUnit.SECONDS));
   assertEquals(0,products.findById(id).orElseThrow().getQuantity());
  }finally{
   start.countDown();
   pool.shutdownNow();
  }
 }
}
