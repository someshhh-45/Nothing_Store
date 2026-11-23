package com.eshop.Ecommerce.extension;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Product variants are backed by different Product IDs, not untrusted client-side labels. */
class MarketplaceSizeProfilesTest {
    @Test
    void shoesHaveUkSizesAndNonzeroFootLengths() {
        assertEquals("SHOE", MarketplaceExpansionSeed.sizeType("VANTA Street Runner Sneakers"));
        var sizes=MarketplaceExpansionSeed.sizesFor("SHOE");
        assertEquals(5, sizes.size());
        assertEquals(Set.of("UK 6","UK 7","UK 8","UK 9","UK 10"), Set.copyOf(sizes));
        assertEquals(26.2, MarketplaceExpansionSeed.shoeLength("UK 8"), 0.001);
        assertTrue(MarketplaceExpansionSeed.shoeLength("UK 6") < MarketplaceExpansionSeed.shoeLength("UK 10"));
    }
    @Test
    void apparelSizesAreNotAppliedToOneSizeAccessories() {
        assertEquals("APPAREL", MarketplaceExpansionSeed.sizeType("LOOM Essential Oversized Tee"));
        assertEquals("APPAREL", MarketplaceExpansionSeed.sizeType("MORROW Soft Fleece Hoodie"));
        assertEquals(Set.of("S","M","L","XL"), Set.copyOf(MarketplaceExpansionSeed.sizesFor("APPAREL")));
        assertNull(MarketplaceExpansionSeed.sizeType("NORTHLINE Trail Daypack"));
        assertNull(MarketplaceExpansionSeed.sizeType("ARCADE Sunglasses"));
        assertNull(MarketplaceExpansionSeed.shoeLength("M"));
    }
}
