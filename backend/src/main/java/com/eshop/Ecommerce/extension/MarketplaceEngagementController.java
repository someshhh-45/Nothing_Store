package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Database-backed wishlist, verified-purchase feedback, and category recommendations. */
@RestController
public class MarketplaceEngagementController {
    private final AuthUtil auth;
    private final ProductRepo products;
    private final WishlistRepository wishlist;
    private final ProductReviewRepository reviews;
    private final OrderItemRepo orderItems;

    public MarketplaceEngagementController(AuthUtil auth,ProductRepo products,
            WishlistRepository wishlist,ProductReviewRepository reviews,OrderItemRepo orderItems) {
        this.auth=auth;this.products=products;this.wishlist=wishlist;
        this.reviews=reviews;this.orderItems=orderItems;
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(()->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
    }

    private Map<String,Object> summary(Product p) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("productId",p.getProductId());result.put("name",p.getProductName());
        result.put("category",p.getCategory()==null?"Other":p.getCategory().getCategoryName());
        result.put("brand",p.getBrand());result.put("image",p.getImage());
        result.put("price",p.getSpecialPrice());result.put("stock",p.getQuantity());
        result.put("variantLabel",p.getVariantLabel());
        return result;
    }

    @GetMapping("/api/customer/wishlist")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> myWishlist() {
        return wishlist.findByUser_UseridOrderByCreatedAtDesc(auth.loggedInUserId()).stream()
            .map(entry->summary(entry.getProduct())).toList();
    }

    @PutMapping("/api/customer/wishlist/{productId}")
    @Transactional
    public Map<String,Object> save(@PathVariable Long productId) {
        User user=auth.loggedInUser();
        if(!wishlist.existsByUser_UseridAndProduct_ProductId(user.getUserid(),productId)){
            WishlistEntry entry=new WishlistEntry();
            entry.setUser(user);entry.setProduct(product(productId));
            wishlist.saveAndFlush(entry);
        }
        return Map.of("productId",productId,"saved",true);
    }

    @DeleteMapping("/api/customer/wishlist/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unsave(@PathVariable Long productId) {
        wishlist.deleteByUser_UseridAndProduct_ProductId(auth.loggedInUserId(),productId);
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating,
                                @NotBlank @Size(max=1000) String comment) {}
    private Map<String,Object> reviewView(ProductReview r) {
        return Map.of("rating",r.getRating(),"comment",r.getComment(),
            "author",r.getUser().getUserName(),"updatedAt",r.getUpdatedAt().toString());
    }

    @GetMapping("/api/store/products/{productId}/reviews")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> productReviews(@PathVariable Long productId) {
        product(productId);
        return reviews.findTop30ByProduct_ProductIdOrderByUpdatedAtDesc(productId)
            .stream().map(this::reviewView).toList();
    }

    @PostMapping("/api/customer/products/{productId}/reviews")
    @Transactional
    public Map<String,Object> review(@PathVariable Long productId,@Valid @RequestBody ReviewRequest req) {
        User user=auth.loggedInUser();
        Product p=product(productId);
        // Only customers who actually placed an order for this product can review it.
        if(orderItems.countPurchasedByEmailAndProduct(user.getEmail(),productId)==0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Purchase this product before reviewing it");
        ProductReview entry=reviews.findByUser_UseridAndProduct_ProductId(user.getUserid(),productId)
            .orElseGet(()->{ProductReview r=new ProductReview();r.setUser(user);r.setProduct(p);return r;});
        entry.setRating(req.rating());entry.setComment(req.comment().trim());
        entry.setUpdatedAt(Instant.now());
        return reviewView(reviews.save(entry));
    }

    @GetMapping("/api/store/recommendations")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> recommendations(@RequestParam Long productId) {
        Product reference=product(productId);
        if(reference.getCategory()==null)return List.of();
        Long categoryId=reference.getCategory().getCategoryId();
        return products.findAll().stream()
            .filter(p->!p.getProductId().equals(productId)
                && p.getCategory()!=null && Objects.equals(p.getCategory().getCategoryId(),categoryId)
                && p.getQuantity()!=null && p.getQuantity()>0)
            .sorted(Comparator.comparingInt((Product p)->
                Objects.equals(p.getBrand(),reference.getBrand())?0:1)
                .thenComparing(Product::getProductId))
            .limit(6).map(this::summary).toList();
    }
}
