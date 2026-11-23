package com.eshop.Ecommerce.Controller;

import com.eshop.Ecommerce.Model.Cart;
import com.eshop.Ecommerce.Payload.CartDTO;
import com.eshop.Ecommerce.Payload.CartItemDTO;
import com.eshop.Ecommerce.Repositories.CartRepository;
import com.eshop.Ecommerce.Service.CartService;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CartController {

    @Autowired
    private CartService cartService;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private CartRepository cartRepository;

    @PostMapping("/carts/products/{productId}/quantity/{quantity}")
    public ResponseEntity<CartDTO> addProductToCart(@PathVariable Long productId,
                                                    @PathVariable  Integer quantity) {
        CartDTO cartDto=cartService.addProductToCart(productId,quantity);
        return new ResponseEntity<>(cartDto, HttpStatus.CREATED);
    }
    @GetMapping("/carts")
    public ResponseEntity<List<CartDTO>>getCarts(){
        String email=authUtil.loggedInEmail();
        Cart own=cartRepository.findCartByEmail(email);
        List<CartDTO> carts=own==null?List.of():List.of(cartService.getCartById(email,own.getCartId()));
        return new ResponseEntity<>(carts, HttpStatus.OK);
    }
    @Transactional
    @PostMapping("/cart/create")
    public ResponseEntity<String> createOrUpdateCart(@RequestBody List<CartItemDTO> cartItems){
        String response = cartService.createOrUpdateCartWithItems(cartItems);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @GetMapping("/carts/users/cart")
    public ResponseEntity<CartDTO> getCartById(){
        String emailId=authUtil.loggedInEmail();
        Cart cart=cartRepository.findCartByEmail(emailId);
        System.out.println(cart);
        Long cartId=cart.getCartId();
        CartDTO cartDTO= cartService.getCartById(emailId,cartId);
        return new ResponseEntity<>(cartDTO, HttpStatus.OK);
    }
    @PutMapping("/cart/products/{productId}/quantity/{operation}")
    public ResponseEntity<CartDTO> updateCartProduct(@PathVariable Long productId, @PathVariable  String operation){
        CartDTO cartDTO=cartService.updateProductQuantityInCart(productId,
                operation.equalsIgnoreCase("delete")?-1:1);
        return new ResponseEntity<>(cartDTO, HttpStatus.OK);
    }

    @DeleteMapping("/carts/{cartId}/product/{productId}")
    public ResponseEntity<String> deleteProductFromCart(@PathVariable Long cartId,
                                                        @PathVariable Long productId) {
        Cart own=cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if(own==null || !own.getCartId().equals(cartId)) throw new org.springframework.security.access.AccessDeniedException("Not your cart");
        String status = cartService.deleteProductFromCart(cartId, productId);

        return new ResponseEntity<String>(status, HttpStatus.OK);
    }
}
