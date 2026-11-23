package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Service.RetailerPricing;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.util.Map;
import java.util.Objects;

/** Privileged stock/discount management with row locking and cart price refresh. */
@RestController
public class InventoryManagementController {
    private final ProductRepo products;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final RetailerPricing pricing;
    private final DomainEvents events;
    private final AuthUtil auth;

    public InventoryManagementController(ProductRepo products,CartRepository carts,
        CartItemRepository cartItems,RetailerPricing pricing,DomainEvents events,AuthUtil auth){
        this.products=products;this.carts=carts;this.cartItems=cartItems;
        this.pricing=pricing;this.events=events;this.auth=auth;
    }
    public record StockUpdate(@NotNull @Min(0) @Max(1000000) Integer quantity){}
    public record DiscountUpdate(@NotNull @DecimalMin("0.0") @DecimalMax("80.0") Double percent){}

    private Product product(Long id,boolean seller){
        Product p=products.findByIdForInventoryUpdate(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        if(seller && (p.getUser()==null || !Objects.equals(p.getUser().getUserid(),auth.loggedInUserId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the owning seller may change this SKU");
        return p;
    }
    private Map<String,Object> summary(Product p){
        return Map.of("productId",p.getProductId(),"name",p.getProductName(),
            "stock",p.getQuantity(),"discount",p.getDiscount(),
            "price",p.getPrice(),"specialPrice",p.getSpecialPrice());
    }
    private Map<String,Object> updateStock(Long id,StockUpdate update,boolean seller){
        Product p=product(id,seller);
        p.setQuantity(update.quantity());
        products.save(p);
        events.record("INVENTORY_CHANGED",p.getProductId(),null);
        return summary(p);
    }
    private Map<String,Object> updateDiscount(Long id,DiscountUpdate update,boolean seller){
        Product p=product(id,seller);
        if(p.getPrice()<=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Product has no valid list price");
        double percent=update.percent();
        double price=BigDecimal.valueOf(p.getPrice())
            .multiply(BigDecimal.valueOf(100-percent))
            .divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP).doubleValue();
        p.setDiscount(percent);p.setSpecialPrice(price);
        products.save(p);
        // Recalculate each customer's saved cart using THEIR approved retailer status.
        // The legacy admin product update used the logged-in admin's price for all carts.
        for(Cart cart:carts.findCartsByProductId(id)){
            CartItem line=cartItems.findCartItemByProductIdAndCartId(cart.getCartId(),id);
            if(line==null)continue;
            double oldLinePrice=line.getProductPrice();
            double newLinePrice=pricing.customerPrice(cart.getUser(),price);
            line.setProductPrice(newLinePrice);line.setDiscount(percent);
            double previousTotal=cart.getTotalPrice()==null?0:cart.getTotalPrice();
            cart.setTotalPrice(BigDecimal.valueOf(previousTotal+(newLinePrice-oldLinePrice)*line.getQuantity())
                .setScale(2,RoundingMode.HALF_UP).doubleValue());
            cartItems.save(line);carts.save(cart);
        }
        events.record("PRODUCT_CHANGED",p.getProductId(),null);
        return summary(p);
    }

    @PutMapping("/api/admin/products/{id}/stock")
    @Transactional
    public Map<String,Object> adminStock(@PathVariable Long id,@Valid @RequestBody StockUpdate change){
        return updateStock(id,change,false);
    }
    @PutMapping("/api/seller/products/{id}/stock")
    @Transactional
    public Map<String,Object> sellerStock(@PathVariable Long id,@Valid @RequestBody StockUpdate change){
        return updateStock(id,change,true);
    }
    @PutMapping("/api/admin/products/{id}/discount")
    @Transactional
    public Map<String,Object> adminDiscount(@PathVariable Long id,@Valid @RequestBody DiscountUpdate change){
        return updateDiscount(id,change,false);
    }
    @PutMapping("/api/seller/products/{id}/discount")
    @Transactional
    public Map<String,Object> sellerDiscount(@PathVariable Long id,@Valid @RequestBody DiscountUpdate change){
        return updateDiscount(id,change,true);
    }
}
