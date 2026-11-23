package com.eshop.Ecommerce.Controller;

import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Payload.AddressDTO;
import com.eshop.Ecommerce.Service.AddressService;
import com.eshop.Ecommerce.Util.AuthUtil;
import com.eshop.Ecommerce.Repositories.AddressRepository;
import com.eshop.Ecommerce.Exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AddressController {

    @Autowired
    AuthUtil authUtil;

    @Autowired
    AddressService addressService;
    @Autowired
    AddressRepository addressRepository;

    private void requireAddressOwner(Long addressId){
        var saved=addressRepository.findById(addressId).orElseThrow(()->new ResourceNotFoundException("Address","addressId",addressId));
        if(saved.getUser()==null || !saved.getUser().getUserid().equals(authUtil.loggedInUser().getUserid()))
            throw new AccessDeniedException("You cannot access another customer's address");
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressDTO> createAddress(@Valid @RequestBody AddressDTO addressDTO){
        if((addressDTO.getLatitude()==null)!=(addressDTO.getLongitude()==null))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Coordinates must be provided together");
        User user = authUtil.loggedInUser();
        AddressDTO savedAddressDTO = addressService.createAddress(addressDTO, user);
        return new ResponseEntity<>(savedAddressDTO, HttpStatus.CREATED);
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<AddressDTO>> getAddresses(){
        List<AddressDTO> addressList = addressService.getUserAddresses(authUtil.loggedInUser());
        return new ResponseEntity<>(addressList, HttpStatus.OK);
    }

    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<AddressDTO> getAddressById(@PathVariable Long addressId){
        requireAddressOwner(addressId);
        AddressDTO addressDTO = addressService.getAddressesById(addressId);
        return new ResponseEntity<>(addressDTO, HttpStatus.OK);
    }


    @GetMapping("/users/addresses")
    public ResponseEntity<List<AddressDTO>> getUserAddresses(){
        User user = authUtil.loggedInUser();
        List<AddressDTO> addressList = addressService.getUserAddresses(user);
        return new ResponseEntity<>(addressList, HttpStatus.OK);
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<AddressDTO> updateAddress(@PathVariable Long addressId
            , @RequestBody AddressDTO addressDTO){
        requireAddressOwner(addressId);
        if((addressDTO.getLatitude()==null)!=(addressDTO.getLongitude()==null))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Coordinates must be provided together");
        AddressDTO updatedAddress = addressService.updateAddress(addressId, addressDTO);
        return new ResponseEntity<>(updatedAddress, HttpStatus.OK);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<String> updateAddress(@PathVariable Long addressId){
        requireAddressOwner(addressId);
        String status = addressService.deleteAddress(addressId);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }
}