package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Model.Address;
import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Payload.AddressDTO;
import com.eshop.Ecommerce.Repositories.AddressRepository;
import com.eshop.Ecommerce.Repositories.UserRepository;
import com.eshop.Ecommerce.Service.AddressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Models the normal checkout path with a User loaded outside a transaction. */
@SpringBootTest
class CheckoutAddressIntegrationTest {
 @Autowired UserRepository users;
 @Autowired AddressRepository addresses;
 @Autowired AddressService service;

 @Test
 void createsAndListsCustomerAddressesWithoutLazyCollectionAccess() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  User buyer=users.save(new User("addr"+suffix,"addr"+suffix+"@example.org","unused-password-hash"));
  AddressDTO address=new AddressDTO();
  address.setStreet("Main Test Road");
  address.setBuildingName("Apartment Alpha");
  address.setCity("Bengaluru");address.setState("Karnataka");
  address.setCountry("India");address.setPincode("560001");
  // This user entity was returned by a repository call with no open web view/session.
  assertTrue(service.getUserAddresses(buyer).isEmpty());
  AddressDTO created=service.createAddress(address,buyer);
  assertNotNull(created.getAddressId());
  assertEquals(1,service.getUserAddresses(buyer).size());
  assertEquals("Bengaluru",service.getUserAddresses(buyer).get(0).getCity());
  assertEquals(buyer.getUserid(),addresses.findById(created.getAddressId()).orElseThrow().getUser().getUserid());
 }
}
