package com.eshop.Ecommerce;

import com.eshop.Ecommerce.extension.MarketplaceExpansionSeed;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class MarketplaceCatalogDatasetTest {
    @Test
    void catalogContainsMultipleBrandsAndRelevantPhotoReferencesInEveryCollection() {
        var products = MarketplaceExpansionSeed.readEntries();
        assertTrue(products.size() >= 56);
        var categories = Set.of("Fashion", "Toys & Games", "Electronics", "Gadgets");
        for (String category : categories) {
            var entries = products.stream().filter(p -> category.equals(p.category())).toList();
            assertTrue(entries.size() >= 10, category + " requires diverse products");
            assertTrue(entries.stream().map(p -> p.brand()).distinct().count() >= 4, category + " requires multiple brands");
            assertTrue(entries.stream().map(p -> p.image()).distinct().count() >= 4, category + " requires several photos");
        }
        assertTrue(products.stream().map(p -> p.brand()).collect(Collectors.toSet()).size() >= 20);
        assertTrue(products.stream().allMatch(p -> p.description().length() >= 10));
        assertTrue(products.stream().allMatch(p -> p.quantity() > 0 && p.price() > 0));
        assertTrue(products.stream().allMatch(p -> p.image().startsWith("https://images.unsplash.com/photo-")));
    }
}