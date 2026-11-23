package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Append-only, idempotent DEMONSTRATION data. Does not reset orders, inventory,
 * merchant-managed pricing, or existing product photographs on restart.
 * Brand names are fictional sample labels; photos are illustrative only.
 */
@Component
public class MarketplaceExpansionSeed {
    private final ProductRepo products;
    private final CategoryRepo categories;
    private final DomainEvents events;
    @Value("${SEED_RETAIL_CATALOG:false}") private boolean enabled;

    public MarketplaceExpansionSeed(ProductRepo products, CategoryRepo categories, DomainEvents events) {
        this.products = products;
        this.categories = categories;
        this.events = events;
    }

    public record Entry(String category, String brand, String name, double price,
                        String description, String city, int quantity, String image) {}

    public static List<Entry> readEntries() {
        var resource = new ClassPathResource("catalog/marketplace-v2.tsv");
        List<Entry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\\|", -1);
                if (f.length != 8) throw new IllegalArgumentException("Invalid demo catalog row: expected 8 columns");
                var entry = new Entry(f[0], f[1], f[2], Double.parseDouble(f[3]), f[4], f[5],
                        Integer.parseInt(f[6]), f[7]);
                if (entry.name().isBlank() || entry.brand().isBlank() || entry.price() <= 0 ||
                    entry.quantity() <= 0 || !entry.image().startsWith("https://images.unsplash.com/")) {
                    throw new IllegalArgumentException("Invalid demo product: " + entry.name());
                }
                if (!names.add(entry.name().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate demo product name: " + entry.name());
                entries.add(entry);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read bundled marketplace demo catalog", e);
        }
        return List.copyOf(entries);
    }

    @Transactional
    @org.springframework.core.annotation.Order(20)
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) return;
        Map<String, Category> byCategory = new HashMap<>();
        for (Category c : categories.findAll()) byCategory.put(c.getCategoryName().toLowerCase(Locale.ROOT), c);
        Map<String, Product> byName = new HashMap<>();
        for (Product p : products.findAll()) byName.put(p.getProductName().toLowerCase(Locale.ROOT), p);
        for (Entry entry : readEntries()) {
            Product existing = byName.get(entry.name().toLowerCase(Locale.ROOT));
            if (existing != null) {
                boolean changed = false;
                if (existing.getBrand() == null || existing.getBrand().isBlank()) {
                    existing.setBrand(entry.brand());
                    changed = true;
                }
                if (existing.getImage() == null || existing.getImage().isBlank()) {
                    existing.setImage(entry.image());
                    changed = true;
                }
                if (changed) {
                    products.save(existing);
                    events.record("PRODUCT_CHANGED", existing.getProductId(), null);
                }
                continue;
            }
            Category category = byCategory.get(entry.category().toLowerCase(Locale.ROOT));
            if (category == null) {
                category = new Category();
                category.setCategoryName(entry.category());
                category = categories.save(category);
                byCategory.put(entry.category().toLowerCase(Locale.ROOT), category);
            }
            Product p = new Product();
            p.setProductName(entry.name());
            p.setBrand(entry.brand());
            p.setDescription(entry.description());
            p.setPrice(entry.price());
            p.setSpecialPrice(entry.price());
            p.setDiscount(0);
            p.setQuantity(entry.quantity());
            p.setCity(entry.city());
            p.setImage(entry.image());
            p.setCategory(category);
            Product saved = products.save(p);
            byName.put(entry.name().toLowerCase(Locale.ROOT), saved);
            events.record("PRODUCT_CHANGED", saved.getProductId(), null);
        }
        // Preserve existing product IDs and prices; create additional stock-bearing
        // demo SKUs only for fashion items that genuinely require a selectable size.
        seedFashionSizes();
    }

    static String sizeType(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        if (s.matches(".*(sneaker|shoe|trainer|boot|running|footwear).*")) return "SHOE";
        if (s.matches(".*(t-shirt|tee|hoodie|jacket|shirt|sweatshirt|denim|trouser|jean|dress|socks|knit).*")) return "APPAREL";
        return null;
    }

    static List<String> sizesFor(String type) {
        return "SHOE".equals(type)
            ? List.of("UK 6","UK 7","UK 8","UK 9","UK 10")
            : List.of("S","M","L","XL");
    }

    static Double shoeLength(String size) {
        return switch(size) {
            case "UK 6" -> 24.6; case "UK 7" -> 25.4;
            case "UK 8" -> 26.2; case "UK 9" -> 27.1;
            case "UK 10" -> 27.9; default -> null;
        };
    }

    private void seedFashionSizes() {
        // Never modify seller-managed fashion products. Only demo names are eligible.
        Set<String> demoNames=new HashSet<>();
        for(Entry entry:readEntries()) if("Fashion".equals(entry.category())) demoNames.add(entry.name());
        demoNames.addAll(Set.of(
            "Classic Cotton T-Shirt","Urban Running Shoes","Unisex Hoodie",
            "Cotton Crew Socks 5 Pack","Classic Denim Jacket"
        ));
        List<Product> all = products.findAll();
        Map<String,Product> byName = new HashMap<>();
        for(Product p:all) byName.put(p.getProductName().toLowerCase(Locale.ROOT),p);
        for(Product base:all) {
            if(base.getCategory()==null || !"Fashion".equalsIgnoreCase(base.getCategory().getCategoryName())) continue;
            if(base.getUser()!=null || !demoNames.contains(base.getProductName()) || base.getQuantity()==null) continue;
            if(base.getVariantGroup()!=null && !base.getVariantGroup().isBlank()) continue;
            String kind=sizeType(base.getProductName());
            if(kind==null) continue; // Bags/wallets/etc remain one-size items.
            String group=base.getProductName();
            String defaultSize="SHOE".equals(kind)?"UK 8":"M";
            base.setVariantGroup(group);
            base.setVariantLabel(defaultSize);
            base.setSizeType(kind);
            base.setFootLengthCm(shoeLength(defaultSize));
            products.save(base);
            events.record("PRODUCT_CHANGED",base.getProductId(),null);
            for(String size:sizesFor(kind)) {
                if(size.equals(defaultSize))continue;
                String name=group+" / "+size;
                Product variant=byName.get(name.toLowerCase(Locale.ROOT));
                if(variant==null) {
                    variant=new Product();
                    variant.setProductName(name);variant.setDescription(base.getDescription());
                    variant.setBrand(base.getBrand());variant.setCategory(base.getCategory());
                    variant.setPrice(base.getPrice());variant.setSpecialPrice(base.getSpecialPrice());
                    variant.setDiscount(base.getDiscount());variant.setQuantity(Math.max(3,base.getQuantity()/4));
                    variant.setCity(base.getCity());variant.setImage(base.getImage());
                }
                variant.setVariantGroup(group);variant.setVariantLabel(size);
                variant.setSizeType(kind);variant.setFootLengthCm(shoeLength(size));
                Product saved=products.save(variant);
                byName.put(name.toLowerCase(Locale.ROOT), saved);
                events.record("PRODUCT_CHANGED",saved.getProductId(),null);
            }
        }
    }
}