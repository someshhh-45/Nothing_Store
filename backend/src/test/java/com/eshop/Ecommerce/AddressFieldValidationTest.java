package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Payload.AddressDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressFieldValidationTest {
    @Test
    void rejectsInvalidPinAndMobileFormats() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var address = new AddressDTO();
            address.setPincode("000000");
            address.setPhoneNumber("1234567890");
            var violations = validator.validate(address);
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("pincode")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber")));

            address.setPincode("560001");
            address.setPhoneNumber("9876543210");
            assertTrue(validator.validate(address).isEmpty());
        }
    }
}
