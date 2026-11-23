package com.eshop.Ecommerce.extension;
import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class CoffeeCatalogSeed {
 private final CategoryRepo categories;private final ProductRepo products;private final DomainEvents events;
 @Value("${SEED_COFFEE_CATALOG:false}") private boolean enabled;
 public CoffeeCatalogSeed(CategoryRepo categories,ProductRepo products,DomainEvents events){
 this.categories=categories;this.products=products;this.events=events;
 }
 @Transactional
 @EventListener(ApplicationReadyEvent.class)
 public void seed(){
  if(!enabled)return;
  Category coffee=categories.findAll().stream()
   .filter(c->"Coffee".equalsIgnoreCase(c.getCategoryName())).findFirst().orElseGet(()->{
    Category c=new Category();c.setCategoryName("Coffee");return categories.save(c);
   });
  add(coffee,"Original Black",149,"Bold cold brew coffee with a smooth finish","Bengaluru");
  add(coffee,"Toasted Vanilla",159,"Vanilla cold brew coffee with a smooth finish","Mumbai");
  add(coffee,"Smooth Mocha",169,"Mocha cold brew with rich cocoa notes","Bengaluru");
  add(coffee,"Variety Pack",549,"Assorted coffee flavor variety pack","Mumbai");
 }
 private void add(Category category,String name,double price,String description,String city){
  if(products.findAll().stream().anyMatch(p->name.equalsIgnoreCase(p.getProductName())))return;
  Product p=new Product();p.setProductName(name);p.setDescription(description);
  p.setQuantity(100);p.setPrice(price);p.setDiscount(0);p.setSpecialPrice(price);
  p.setCategory(category);p.setCity(city);
  Product saved=products.save(p);
  events.record("PRODUCT_CHANGED",saved.getProductId(),null);
 }
}