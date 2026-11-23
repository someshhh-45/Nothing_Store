package com.eshop.Ecommerce.extension;
import com.eshop.Ecommerce.Model.Category;
import com.eshop.Ecommerce.Model.Product;
import com.eshop.Ecommerce.Repositories.CategoryRepo;
import com.eshop.Ecommerce.Repositories.ProductRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Component
public class RetailCatalogSeed {
 private final CategoryRepo categories;
 private final ProductRepo products;
 private final DomainEvents events;
 @Value("${SEED_RETAIL_CATALOG:false}") private boolean enabled;
 public RetailCatalogSeed(CategoryRepo categories,ProductRepo products,DomainEvents events){
   this.categories=categories;this.products=products;this.events=events;
 }
 @Transactional @org.springframework.core.annotation.Order(10) @EventListener(ApplicationReadyEvent.class)
 public void seed(){
  if(!enabled)return;
  add("Electronics","Bluetooth Wireless Earbuds",1999,"Noise-isolating true wireless earbuds with charging case", "Bengaluru",40);
  add("Electronics","Portable Bluetooth Speaker",2499,"Rechargeable portable speaker for music on the go","Mumbai",35);
  add("Electronics","USB-C Fast Charger 45W",899,"Compact fast charging adapter compatible with USB-C devices","Delhi",75);
  add("Electronics","Smart Fitness Watch",3299,"Smartwatch with fitness tracking and colorful display","Hyderabad",45);
  add("Home & Living","Stainless Steel Water Bottle",599,"Insulated reusable stainless steel bottle for everyday use","Bengaluru",90);
  add("Home & Living","Premium Cotton Bedsheet",1299,"Soft breathable cotton bedsheet for a comfortable bedroom","Mumbai",32);
  add("Home & Living","LED Desk Lamp",1099,"Adjustable energy-saving reading and study desk lamp","Pune",54);
  add("Fashion","Everyday Canvas Backpack",1499,"Durable lightweight backpack with laptop compartment","Delhi",65);
  add("Fashion","Classic Cotton T-Shirt",499,"Comfortable regular-fit cotton t-shirt for daily wear","Bengaluru",120);
  add("Fashion","Urban Running Shoes",2199,"Lightweight breathable sports shoes with cushioned sole","Hyderabad",34);
  add("Grocery","Premium Basmati Rice 1kg",189,"Long-grain aromatic premium basmati rice one kilogram","Mumbai",150);
  add("Grocery","Organic Almonds 500g",449,"Crunchy whole almonds packed in a sealed reusable pouch","Delhi",80);
  add("Beauty","Daily Sunscreen SPF 50",399,"Lightweight broad-spectrum sunscreen for everyday protection","Pune",100);
  add("Beauty","Hydrating Face Wash",299,"Gentle facial cleanser with moisturizing ingredients","Bengaluru",110);
  add("Electronics","Noise Cancelling Headphones",4499,"Over-ear wireless headphones with active noise cancellation and foldable cushions","Bengaluru",28);
  add("Electronics","10000mAh Power Bank",1299,"Compact portable battery pack with dual USB output and fast charging","Pune",82);
  add("Electronics","Wireless Keyboard and Mouse Combo",1599,"Quiet rechargeable keyboard and ergonomic mouse for your work desk","Delhi",56);
  add("Electronics","Laptop Stand Aluminium",899,"Adjustable aluminium laptop stand with ventilated non-slip support","Mumbai",73);
  add("Electronics","1080p HD Webcam",1799,"Full HD webcam for video meetings and streaming with privacy cover","Hyderabad",37);
  add("Electronics","USB-C Multiport Hub",1899,"Seven in one USB-C hub with HDMI, USB ports and memory card slots","Bengaluru",48);
  add("Electronics","Wireless Charging Pad",1199,"Slim Qi-compatible charging pad with anti-slip surface and LED indicator","Delhi",65);
  add("Electronics","Portable SSD 512GB",3999,"Fast compact external solid state drive for backups and large transfers","Mumbai",24);
  add("Home & Living","Ceramic Coffee Mug Set",749,"Set of four microwave-safe ceramic mugs with a minimalist finish","Pune",62);
  add("Home & Living","Nonstick Frying Pan 24cm",1199,"Durable everyday nonstick skillet with heat-resistant ergonomic handle","Bengaluru",39);
  add("Home & Living","Aroma Diffuser 300ml",1499,"Quiet ultrasonic fragrance diffuser with soft ambient mood lighting","Hyderabad",52);
  add("Home & Living","Cotton Cushion Cover Pair",599,"Two textured cotton cushion covers with concealed zipper closures","Mumbai",86);
  add("Home & Living","Stackable Storage Boxes",999,"Three durable reusable organization boxes for shelves and closets","Delhi",60);
  add("Home & Living","Digital Kitchen Scale",699,"Compact digital food scale with tare function and gram measurements","Pune",77);
  add("Fashion","Unisex Hoodie",1799,"Soft fleece hooded sweatshirt with relaxed everyday fit and kangaroo pocket","Delhi",43);
  add("Fashion","Minimalist Leather Wallet",899,"Slim wallet with multiple card slots and a compact profile","Bengaluru",67);
  add("Fashion","Polarized Sunglasses",1099,"Lightweight UV400 sunglasses with polarized lenses and storage case","Hyderabad",51);
  add("Fashion","Travel Duffel Bag",1699,"Spacious weekend duffel with detachable strap and inner organizer pocket","Pune",42);
  add("Fashion","Cotton Crew Socks 5 Pack",449,"Five pairs of breathable cushioned crew socks for daily comfort","Mumbai",115);
  add("Fashion","Classic Denim Jacket",2599,"Timeless button-up denim jacket with practical front pockets","Delhi",26);
  add("Grocery","Organic Green Tea 100g",299,"Premium loose-leaf green tea packed to preserve fresh natural aroma","Bengaluru",150);
  add("Grocery","Cold Pressed Groundnut Oil 1L",349,"Single litre traditionally cold-pressed groundnut cooking oil","Pune",97);
  add("Grocery","Natural Peanut Butter 340g",329,"Creamy unsweetened roasted peanut butter without added refined sugar","Mumbai",112);
  add("Grocery","Wholegrain Oats 1kg",219,"One kilogram of fibre-rich wholegrain rolled oats for breakfast","Hyderabad",180);
  add("Grocery","Arabica Coffee Beans 250g",499,"Freshly roasted whole Arabica coffee beans with smooth chocolate notes","Bengaluru",90);
  add("Grocery","Dark Chocolate 70 Percent 100g",199,"Intensely flavoured dark chocolate bar with seventy percent cocoa","Delhi",140);
  add("Beauty","Vitamin C Face Serum 30ml",699,"Lightweight brightening facial serum formulated with vitamin C","Mumbai",95);
  add("Beauty","Aloe Vera Moisturizer 100ml",399,"Quick-absorbing aloe moisturizer for comfortable everyday hydration","Bengaluru",125);
  add("Beauty","Gentle Shampoo 300ml",349,"Mild daily-use shampoo for a clean and refreshed scalp","Hyderabad",105);
  add("Beauty","Lip Balm Variety Pack",299,"Set of three moisturizing lip balms with subtle everyday flavours","Pune",117);
  add("Beauty","Hand Cream Shea Butter 75ml",279,"Nourishing hand cream for dry hands with a light non-greasy texture","Delhi",100);
  add("Beauty","Daily Body Lotion 400ml",449,"Fast-absorbing body lotion for everyday use with lasting hydration","Mumbai",85);
 }
 private void add(String categoryName,String name,double price,String description,String city,int stock){
  java.util.Optional<Product> existing=products.findAll().stream()
     .filter(p->name.equalsIgnoreCase(p.getProductName())).findFirst();
  if(existing.isPresent()){
    Product current=existing.get();
    if(current.getImage()==null||current.getImage().isBlank()){
      current.setImage(imageFor(name));products.save(current);
      events.record("PRODUCT_CHANGED",current.getProductId(),null);
    }
    return;
  }
  Category category=categories.findAll().stream().filter(c->categoryName.equalsIgnoreCase(c.getCategoryName()))
     .findFirst().orElseGet(()->{Category c=new Category();c.setCategoryName(categoryName);return categories.save(c);});
  Product p=new Product();p.setProductName(name);p.setDescription(description);
  p.setQuantity(stock);p.setPrice(price);p.setSpecialPrice(price);p.setDiscount(0);
  p.setCity(city);p.setCategory(category);p.setImage(imageFor(name));
  Product stored=products.save(p);events.record("PRODUCT_CHANGED",stored.getProductId(),null);
 }
 private String imageFor(String name){
   return switch(name){
     case "Bluetooth Wireless Earbuds" -> "https://images.unsplash.com/photo-1754142654807-1dfcdc3f7f22?auto=format&fit=crop&w=800&q=82";
     case "Portable Bluetooth Speaker" -> "https://images.unsplash.com/photo-1732229033839-c76b4071c449?auto=format&fit=crop&w=800&q=82";
     case "USB-C Fast Charger 45W" -> "https://images.unsplash.com/photo-1725304382197-663ae3864750?auto=format&fit=crop&w=800&q=82";
     case "Smart Fitness Watch" -> "https://images.unsplash.com/photo-1638450566735-ead9df13ef3f?auto=format&fit=crop&w=800&q=82";
     case "Stainless Steel Water Bottle" -> "https://images.unsplash.com/photo-1700506844206-ec2f9e2a65cd?auto=format&fit=crop&w=800&q=82";
     case "Premium Cotton Bedsheet" -> "https://images.unsplash.com/photo-1515510621228-30de609bbd60?auto=format&fit=crop&w=800&q=82";
     case "LED Desk Lamp" -> "https://images.unsplash.com/photo-1731762524352-b5663f83a830?auto=format&fit=crop&w=800&q=82";
     case "Everyday Canvas Backpack" -> "https://images.unsplash.com/photo-1711649116396-54e1ba20e828?auto=format&fit=crop&w=800&q=82";
     case "Classic Cotton T-Shirt" -> "https://images.unsplash.com/photo-1651761179569-4ba2aa054997?auto=format&fit=crop&w=800&q=82";
     case "Urban Running Shoes" -> "https://images.unsplash.com/photo-1719523677291-a395426c1a87?auto=format&fit=crop&w=800&q=82";
     case "Premium Basmati Rice 1kg" -> "https://images.unsplash.com/photo-1568347355280-d33fdf77d42a?auto=format&fit=crop&w=800&q=82";
     case "Organic Almonds 500g" -> "https://images.unsplash.com/photo-1788632204503-c8fb6b5a2498?auto=format&fit=crop&w=800&q=82";
     case "Daily Sunscreen SPF 50" -> "https://images.unsplash.com/photo-1738721796968-bc0c4a55960d?auto=format&fit=crop&w=800&q=82";
     case "Hydrating Face Wash" -> "https://images.unsplash.com/photo-1732861612232-50cbe19c1ae5?auto=format&fit=crop&w=800&q=82";
     case "Wireless Keyboard and Mouse Combo" -> "https://images.unsplash.com/photo-1721036469059-ab07e6bb6508?auto=format&fit=crop&w=800&q=82";
     case "Ceramic Coffee Mug Set" -> "https://images.unsplash.com/photo-1522120573867-e574959f84c8?auto=format&fit=crop&w=800&q=82";
     case "Nonstick Frying Pan 24cm" -> "https://images.unsplash.com/photo-1592156328697-079f6ee0cfa5?auto=format&fit=crop&w=800&q=82";
     case "Minimalist Leather Wallet" -> "https://images.unsplash.com/photo-1697551894947-25fba13585e6?auto=format&fit=crop&w=800&q=82";
     case "Classic Denim Jacket" -> "https://images.unsplash.com/photo-1548958142-5319c0ec17ab?auto=format&fit=crop&w=800&q=82";
     case "Natural Peanut Butter 340g" -> "https://images.unsplash.com/photo-1662675115984-4ef64a3651a3?auto=format&fit=crop&w=800&q=82";
     case "Arabica Coffee Beans 250g" -> "https://images.unsplash.com/photo-1769437082791-8c9af44d7c28?auto=format&fit=crop&w=800&q=82";
     case "Dark Chocolate 70 Percent 100g" -> "https://images.unsplash.com/photo-1633782330753-a346e5303840?auto=format&fit=crop&w=800&q=82";
     case "Vitamin C Face Serum 30ml" -> "https://images.unsplash.com/photo-1777450793530-99a0e7b7fc53?auto=format&fit=crop&w=800&q=82";
     case "Noise Cancelling Headphones" -> "https://images.unsplash.com/photo-1599669454515-1b2e0173f302?auto=format&fit=crop&w=800&q=82";
     case "10000mAh Power Bank" -> "https://images.unsplash.com/photo-1601898532138-9f145a2ad69e?auto=format&fit=crop&w=800&q=82";
     case "Laptop Stand Aluminium" -> "https://images.unsplash.com/photo-1700451761287-b8b21c406208?auto=format&fit=crop&w=800&q=82";
     case "Portable SSD 512GB" -> "https://images.unsplash.com/photo-1756142752564-586c77618757?auto=format&fit=crop&w=800&q=82";
     case "Polarized Sunglasses" -> "https://images.unsplash.com/photo-1711223499758-8aef018b720e?auto=format&fit=crop&w=800&q=82";
     case "Unisex Hoodie" -> "https://images.unsplash.com/photo-1742911072884-3cbf51117247?auto=format&fit=crop&w=800&q=82";
     case "1080p HD Webcam" -> "https://images.unsplash.com/photo-1762681290673-ba1ad4ea0875?auto=format&fit=crop&w=800&q=82";
     case "Wireless Charging Pad" -> "https://images.unsplash.com/photo-1750332191594-d5387a69f7c8?auto=format&fit=crop&w=800&q=82";
     case "Digital Kitchen Scale" -> "https://images.unsplash.com/photo-1772683709393-f0cf8c226872?auto=format&fit=crop&w=800&q=82";
     case "Cotton Cushion Cover Pair" -> "https://images.unsplash.com/photo-1775029676739-86443af4010d?auto=format&fit=crop&w=800&q=82";
     case "Cotton Crew Socks 5 Pack" -> "https://images.unsplash.com/photo-1641399032315-39f5f79f436b?auto=format&fit=crop&w=800&q=82";
     case "Organic Green Tea 100g" -> "https://images.unsplash.com/photo-1767840272016-36dc5dd51f79?auto=format&fit=crop&w=800&q=82";
     case "Cold Pressed Groundnut Oil 1L" -> "https://images.unsplash.com/photo-1757801333068-d52a3e448cde?auto=format&fit=crop&w=800&q=82";
     case "Wholegrain Oats 1kg" -> "https://images.unsplash.com/photo-1510776478953-fa4dc5de04ca?auto=format&fit=crop&w=800&q=82";
     case "Hand Cream Shea Butter 75ml" -> "https://images.unsplash.com/photo-1778854290397-d6ba28d0cdf6?auto=format&fit=crop&w=800&q=82";
     case "Gentle Shampoo 300ml" -> "https://images.unsplash.com/photo-1762827990160-9f2d35fb0fd5?auto=format&fit=crop&w=800&q=82";
     case "Lip Balm Variety Pack" -> "https://images.unsplash.com/photo-1692873634623-142b360483d6?auto=format&fit=crop&w=800&q=82";
     case "Daily Body Lotion 400ml" -> "https://images.unsplash.com/photo-1771481103879-dd0e658fd7be?auto=format&fit=crop&w=800&q=82";
     case "Stackable Storage Boxes" -> "https://images.unsplash.com/photo-1786396798357-ac8ba6704d89?auto=format&fit=crop&w=800&q=82";
     case "Aloe Vera Moisturizer 100ml" -> "https://images.unsplash.com/photo-1774620714979-2c258294fa48?auto=format&fit=crop&w=800&q=82";
     case "USB-C Multiport Hub" -> "https://images.unsplash.com/photo-1760376789478-c1023d2dc007?auto=format&fit=crop&w=800&q=82";
     case "Travel Duffel Bag" -> "https://images.unsplash.com/photo-1774560745344-78667b3594a7?auto=format&fit=crop&w=800&q=82";
     case "Aroma Diffuser 300ml" -> "https://images.unsplash.com/photo-1768471569643-717e823b5f9a?auto=format&fit=crop&w=800&q=82";
     default -> null;
   };
 }
}