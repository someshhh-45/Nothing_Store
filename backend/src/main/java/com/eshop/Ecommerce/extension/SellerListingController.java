package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Util.AuthUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Seller-owned product creation and image upload; reuses products, categories and catalog events. */
@RestController
@RequestMapping("/api/seller/listings")
public class SellerListingController {
    private final ProductRepo products;
    private final CategoryRepo categories;
    private final AuthUtil auth;
    private final DomainEvents events;
    private final Path uploadDirectory;

    public SellerListingController(ProductRepo products, CategoryRepo categories, AuthUtil auth,
                                   DomainEvents events,
                                   @Value("${nothing.upload.dir:/app/product-uploads}") String uploadDirectory) {
        this.products=products;this.categories=categories;this.auth=auth;this.events=events;
        this.uploadDirectory=Path.of(uploadDirectory);
    }

    public record CreateListing(
        @NotNull Long categoryId,
        @NotBlank @Size(min=3,max=150) String productName,
        @NotBlank @Size(min=10,max=255) String description,
        @NotBlank @Size(max=90) String brand,
        @NotBlank @Size(max=120) String city,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") Double price,
        @NotNull @Min(0) @Max(1000000) Integer quantity
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> create(@Valid @RequestBody CreateListing input) {
        if(!Double.isFinite(input.price()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be finite");
        Category category=categories.findById(input.categoryId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid category"));
        Product p=new Product();
        p.setCategory(category);
        p.setUser(auth.loggedInUser());
        p.setProductName(input.productName().trim());
        p.setDescription(input.description().trim());
        p.setBrand(input.brand().trim());
        p.setCity(input.city().trim());
        p.setPrice(input.price());
        p.setSpecialPrice(input.price());
        p.setQuantity(input.quantity());
        p.setDiscount(0);
        Product saved=products.save(p);
        events.record("PRODUCT_CHANGED",saved.getProductId(),null);
        return Map.of("productId",saved.getProductId(),"message","Product created");
    }

    @PostMapping(path="/{productId}/image",consumes="multipart/form-data")
    @Transactional
    public Map<String,Object> uploadImage(@PathVariable Long productId,@RequestParam("image") MultipartFile upload) {
        Product p=products.findById(productId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        if(p.getUser()==null || !p.getUser().getUserid().equals(auth.loggedInUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You may only upload images for your products");
        if(upload.isEmpty()||upload.getSize()>3*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Upload a PNG or JPEG image smaller than 3 MB");
        String type=upload.getContentType();
        if(!"image/png".equals(type)&&!"image/jpeg".equals(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only PNG and JPEG images are supported");
        final BufferedImage image;
        try {
            byte[] bytes=upload.getBytes();
            try(var stream=new ByteArrayInputStream(bytes)){
                image=ImageIO.read(stream);
            }
        } catch(IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image cannot be read");
        }
        if(image==null||image.getWidth()<1||image.getHeight()<1||
           image.getWidth()>4000||image.getHeight()>4000||
           (long)image.getWidth()*image.getHeight()>16_000_000L)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image is invalid or dimensions exceed 4000 pixels");
        // Re-encode pixels instead of storing untrusted file bytes or filenames.
        String format="image/png".equals(type)?"png":"jpg";
        String filename=UUID.randomUUID().toString().toLowerCase(Locale.ROOT)+"."+format;
        try{
            Files.createDirectories(uploadDirectory);
            Path target=uploadDirectory.resolve(filename);
            if(!ImageIO.write(image,format,target.toFile()))
                throw new IOException("No image encoder available");
            p.setImage("/uploads/"+filename);
            products.save(p);
            events.record("PRODUCT_CHANGED",p.getProductId(),null);
            return Map.of("productId",p.getProductId(),"image",p.getImage());
        }catch(IOException ex){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Image storage is temporarily unavailable");
        }
    }
}
