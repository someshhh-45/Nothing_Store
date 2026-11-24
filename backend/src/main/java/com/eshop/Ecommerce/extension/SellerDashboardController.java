package com.eshop.Ecommerce.extension;

import com.eshop.Ecommerce.Repositories.OrderItemRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import com.eshop.Ecommerce.Util.AuthUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller")
public class SellerDashboardController {
 private final ProductRepo products;
 private final OrderItemRepo orders;
 private final AuthUtil auth;

 public SellerDashboardController(ProductRepo products,OrderItemRepo orders,AuthUtil auth){
  this.products=products;this.orders=orders;this.auth=auth;
 }

 @GetMapping("/summary")
 @Transactional(readOnly=true)
 public Map<String,Object> summary(){
  Long sellerId=auth.loggedInUserId();
  Object[] sales=orders.sellerOrderStats(sellerId);
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("listings",products.countByUser_Userid(sellerId));
  result.put("approved",products.countByUser_UseridAndListingStatus(sellerId,"APPROVED"));
  result.put("pending",products.countByUser_UseridAndListingStatus(sellerId,"PENDING"));
  result.put("rejected",products.countByUser_UseridAndListingStatus(sellerId,"REJECTED"));
  result.put("orderCount",((Number)sales[0]).longValue());
  result.put("unitsOrdered",((Number)sales[1]).longValue());
  result.put("orderValue",Math.round(((Number)sales[2]).doubleValue()*100.0)/100.0);
  // Order value includes COD/unpaid items and is NOT settled seller revenue.
  return result;
 }

 @GetMapping("/dashboard/orders")
 @Transactional(readOnly=true)
 public List<Map<String,Object>> orders(@RequestParam(defaultValue="0") int page){
  if(page<0||page>1000)throw new org.springframework.web.server.ResponseStatusException(
      org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid page number");
  Long sellerId=auth.loggedInUserId();
  return orders.findSellerOrderLines(sellerId,PageRequest.of(page,25)).stream().map(item->{
   Map<String,Object> row=new LinkedHashMap<>();
   row.put("orderId",item.getOrder().getOrderId());
   row.put("orderDate",item.getOrder().getOrderDate());
   row.put("orderStatus",item.getOrder().getOrderStatus());
   row.put("productId",item.getProduct().getProductId());
   row.put("name",item.getOrderedProductName()==null?item.getProduct().getProductName():item.getOrderedProductName());
   row.put("quantity",item.getQuantity());row.put("unitPrice",item.getOrderedProductPrice());
   row.put("lineTotal",item.getOrderedProductPrice()*item.getQuantity());
   return row;
  }).toList();
 }
}
