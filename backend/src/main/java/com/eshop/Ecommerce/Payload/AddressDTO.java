package com.eshop.Ecommerce.Payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    private Long addressId;
    private String street;
    private String buildingName;
    private String city;
    private String state;
    private String country;
    private String pincode;
    @jakarta.validation.constraints.Pattern(regexp="[6-9][0-9]{9}", message="Use a valid ten-digit Indian mobile number")
    private String phoneNumber;
    @jakarta.validation.constraints.DecimalMin("-90.0")
    @jakarta.validation.constraints.DecimalMax("90.0")
    private Double latitude;
    @jakarta.validation.constraints.DecimalMin("-180.0")
    @jakarta.validation.constraints.DecimalMax("180.0")
    private Double longitude;

    @jakarta.validation.constraints.AssertTrue(message="Latitude and longitude must be provided together")
    public boolean isLocationPairValid() { return (latitude==null)==(longitude==null); }

    // Preserve older callers using the original seven-argument DTO constructor.
    public AddressDTO(Long addressId,String street,String buildingName,String city,String state,String country,String pincode){
        this(addressId,street,buildingName,city,state,country,pincode,null,null,null);
    }
    // Preserve the existing eight-argument API for address callers.
    public AddressDTO(Long addressId,String street,String buildingName,String city,String state,String country,String pincode,String phoneNumber){
        this(addressId,street,buildingName,city,state,country,pincode,phoneNumber,null,null);
    }
}
