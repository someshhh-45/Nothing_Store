package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Payload.CartItemDTO;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.CartService;
import com.eshop.Ecommerce.Util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class CustomerCartPersistenceTest {
 @Autowired private UserRepository users;
 @Autowired private CategoryRepo categories;
 @Autowired private ProductRepo products;
 @Autowired private CartRepository carts;
 @Autowired private CartItemRepository cartItems;
 @Autowired private CartService service;
 @MockBean private AuthUtil auth;

 @Test
 void cartsPersistSeparatelyAndReadingDoesNotChangeStock() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User alice=users.save(new User("alice"+suffix,"alice"+suffix+"@example.org","dummy-hashed-value"));
  User bob=users.save(new User("bob"+suffix,"bob"+suffix+"@example.org","dummy-hashed-value"));
  Category category=new Category();category.setCategoryName("Store Test "+suffix);category=categories.save(category);
  Product product=new Product();product.setProductName("Portable Speaker "+suffix);
  product.setDescription("Portable wireless test speaker for persistence tests");
  product.setQuantity(18);product.setPrice(899);product.setSpecialPrice(899);product.setCategory(category);
  product=products.saveAndFlush(product);
  Long id=product.getProductId();
  when(auth.loggedInEmail()).thenReturn(alice.getEmail());
  when(auth.loggedInUser()).thenReturn(alice);
  service.createOrUpdateCartWithItems(List.of(new CartItemDTO(id,2)));
  Cart aliceCart=carts.findCartByEmail(alice.getEmail());
  assertNotNull(aliceCart);
  Long aliceCartId=aliceCart.getCartId();
  assertEquals(2,cartItems.findCartItemByProductIdAndCartId(aliceCartId,id).getQuantity());
  // Replace an existing customer's cart, not duplicate its items.
  service.createOrUpdateCartWithItems(List.of(new CartItemDTO(id,3)));
  aliceCart=carts.findCartByEmail(alice.getEmail());
  assertEquals(1,cartItems.findAll().stream().filter(ci->ci.getCart().getCartId().equals(aliceCartId)).count());
  assertEquals(3,cartItems.findCartItemByProductIdAndCartId(aliceCartId,id).getQuantity());
  service.getCartById(alice.getEmail(),aliceCartId);
  assertEquals(18,products.findById(id).orElseThrow().getQuantity());
  when(auth.loggedInEmail()).thenReturn(bob.getEmail());
  when(auth.loggedInUser()).thenReturn(bob);
  service.createOrUpdateCartWithItems(List.of(new CartItemDTO(id,1)));
  Cart bobCart=carts.findCartByEmail(bob.getEmail());
  assertNotNull(bobCart);
  assertNotEquals(aliceCartId,bobCart.getCartId());
  assertEquals(1,cartItems.findCartItemByProductIdAndCartId(bobCart.getCartId(),id).getQuantity());
  assertEquals(3,cartItems.findCartItemByProductIdAndCartId(aliceCartId,id).getQuantity());
 }
}