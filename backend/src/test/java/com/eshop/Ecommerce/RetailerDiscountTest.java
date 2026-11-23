package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Payload.CartItemDTO;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.CartService;
import com.eshop.Ecommerce.Service.RetailerPricing;
import com.eshop.Ecommerce.Util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class RetailerDiscountTest {
 @Autowired private RetailerProfileRepository profiles;
 @Autowired private UserRepository users;
 @Autowired private ProductRepo products;
 @Autowired private CategoryRepo categories;
 @Autowired private CartRepository carts;
 @Autowired private CartService cartService;
 @Autowired private RetailerPricing pricing;
 @MockBean private AuthUtil auth;
 @Test
 void onlyApprovedRetailerGetsDatabaseBackedDiscount(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User retailer=users.save(new User("buyer"+suffix,"buyer"+suffix+"@example.org","test-placeholder"));
  Category category=new Category();category.setCategoryName("Retail Test "+suffix);category=categories.save(category);
  Product product=new Product();product.setProductName("Retail Price Test "+suffix);
  product.setDescription("A properly described test product for retailer checkout");
  product.setQuantity(30);product.setPrice(1000);product.setSpecialPrice(1000);product.setCategory(category);
  product=products.saveAndFlush(product);
  RetailerProfile profile=new RetailerProfile();profile.setUser(retailer);
  profile.setBusinessName("My Retail Business");
  profile.setGstin("29ABCDE1234F1Z5");profile.setStatus("PENDING");
  profile=profiles.saveAndFlush(profile);
  when(auth.loggedInEmail()).thenReturn(retailer.getEmail());
  when(auth.loggedInUser()).thenReturn(retailer);
  assertFalse(pricing.approved(retailer));
  cartService.createOrUpdateCartWithItems(List.of(new CartItemDTO(product.getProductId(),2)));
  assertEquals(2000,carts.findCartByEmail(retailer.getEmail()).getTotalPrice(),0.001);
  profile.setStatus("APPROVED");profiles.saveAndFlush(profile);
  assertTrue(pricing.approved(retailer));
  cartService.createOrUpdateCartWithItems(List.of(new CartItemDTO(product.getProductId(),2)));
  assertEquals(2000*(1-pricing.percent()/100.0),
    carts.findCartByEmail(retailer.getEmail()).getTotalPrice(),0.001);
  assertEquals(30,products.findById(product.getProductId()).orElseThrow().getQuantity());
 }
}
