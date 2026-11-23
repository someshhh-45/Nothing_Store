package com.eshop.Ecommerce.Payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private Long ProductId;
    private String ProductName;
    private String image;
    private String brand;
    private String variantGroup;
    private String variantLabel;
    private String sizeType;
    private Double footLengthCm;
    private String city;
    private Integer quantity;
    private double price;
    private double discount;
    private double specialPrice;
    private String description;


}
