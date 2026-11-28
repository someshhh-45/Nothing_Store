/* Product detail routing and rendering. Dependencies are injected by the storefront. */
window.createProductDetails = function({$,getCatalog,productById,productVariants,canShowProductPhoto,chooseIcon,sellerLabel,money,itemPrice,request,addItem,changeQty,checkout,saveToWishlist,showReviews}){
'use strict';
let detailVersion=0;
const detailHomeTitle=document.title;
function routeProduct(){
 const match=location.hash.match(/^#product\/(\d+)$/);
 const page=$('productDetails');
 if(match&&getCatalog().length){renderProductPage(match[1]);return}
 if(!match&&page&&!page.hidden){
  detailVersion++;page.hidden=true;page.replaceChildren();
  document.body.classList.remove('product-page-open');document.title=detailHomeTitle;
  if(location.hash==='#catalog')$('catalog').scrollIntoView();
 }
}
function showQuickView(id){
 if(!productById(id))return;
 const hash='#product/'+encodeURIComponent(id);
 if(location.hash===hash)renderProductPage(id);else location.hash=hash;
}
function renderProductPage(id){
 const p=productById(id),root=$('productDetails');
 const version=++detailVersion;
 root.replaceChildren();root.hidden=false;document.body.classList.add('product-page-open');
 window.scrollTo(0,0);
 if(!p){root.textContent='Product no longer available. ';const back=document.createElement('a');back.href='#catalog';back.textContent='Back to catalog';root.append(back);return}
 document.title=p.name+' | Nothing Market';
 const variants=productVariants(p);let selected=p;
 const multipleSellers=!p.variantGroup&&variants.length>1;
 const breadcrumb=document.createElement('a');breadcrumb.href='#catalog';breadcrumb.textContent='← BACK TO COLLECTION';root.append(breadcrumb);
 const title=document.createElement('h2');title.textContent='THE DETAIL / '+(p.brand||'NOTHING SELECT');root.append(title);
 const panel=document.createElement('div');panel.className='quick-panel';
 const picture=document.createElement('div');picture.className='quick-photo';
 if(p.image&&canShowProductPhoto(p)){
  const img=document.createElement('img');img.src=p.image;img.alt='Illustrative product image: '+(p.variantGroup||p.name);
  img.onerror=()=>{picture.textContent=chooseIcon(p)};picture.append(img);
 }else picture.textContent=chooseIcon(p);
 const info=document.createElement('div');info.className='quick-info';
 const heading=document.createElement('h3');heading.textContent=p.variantGroup||p.name;
 const desc=document.createElement('p');desc.textContent=p.description;
 const soldBy=document.createElement('p');soldBy.className='seller-byline quick-seller';
 const stock=document.createElement('p');stock.className='quick-stock';
 const price=document.createElement('strong');price.className='quick-price';
 const optionsWrap=document.createElement('div');optionsWrap.className='size-picker';
 const sizeTitle=document.createElement('div');sizeTitle.className='size-picker-head';
 const sizeGuide=document.createElement('p');sizeGuide.className='size-guide';
 const optionButtons=document.createElement('div');optionButtons.className='size-options';
 const add=document.createElement('button');add.className='primary-choice';
 const buy=document.createElement('button');buy.className='outline-button';buy.textContent='BUY NOW';
 function renderSelection(){
  deliverySequence++;deliveryMessage.textContent='';
  soldBy.textContent='SOLD BY · '+sellerLabel(selected);
  price.textContent=money(itemPrice(selected));
  stock.textContent=selected.city+' · '+selected.stock+' in stock'+(selected.footLengthCm?' · approx. foot length '+selected.footLengthCm+' cm':'');
  add.textContent=selected.stock>0?'ADD '+selected.variantLabel?.toUpperCase?.()+' TO BAG ↗':'SOLD OUT';
  if(!selected.variantLabel)add.textContent=selected.stock>0?'ADD TO BAG ↗':'SOLD OUT';
  add.disabled=selected.stock<=0;buy.disabled=add.disabled;
  for(const b of optionButtons.querySelectorAll('button')){
   const v=variants.find(x=>String(x.productId)===b.dataset.variant);
   b.classList.toggle('selected',v===selected);
   b.setAttribute('aria-pressed',String(v===selected));
  }
 }
 if(multipleSellers){
  sizeTitle.textContent='CHOOSE YOUR SELLER';
  for(const offer of variants){
   const b=document.createElement('button');b.type='button';b.className='size-option seller-offer-option';
   b.dataset.variant=String(offer.productId);
   b.textContent=sellerLabel(offer)+' · '+money(itemPrice(offer))+' · '+(offer.stock>0?offer.stock+' in stock':'Sold out')+' · '+(offer.city||'India');
   b.disabled=offer.stock<1;
   b.onclick=()=>{selected=offer;renderSelection()};
   optionButtons.append(b);
  }
  sizeGuide.textContent='Each seller sets their own price and stock. The selected seller supplies your order.';
  optionsWrap.append(sizeTitle,optionButtons,sizeGuide);
 }else if(variants.length>1){
  sizeTitle.textContent=selected.sizeType==='SHOE'?'CHOOSE SHOE SIZE (UK)':'CHOOSE SIZE';
  for(const v of variants){
   const b=document.createElement('button');b.type='button';
   b.textContent=v.variantLabel+(v.footLengthCm?' · '+v.footLengthCm+' cm':'');
   b.className='size-option';b.dataset.variant=String(v.productId);
   b.disabled=v.stock<1;b.onclick=()=>{selected=v;renderSelection()};optionButtons.append(b);
  }
  sizeGuide.textContent=selected.sizeType==='SHOE'?
   'Illustrative UK sizing; lengths are approximate foot measurements. Check manufacturer measurements before buying.' :
   'Demo apparel sizes. Actual chest, shoulder and garment measurements require supplier data.';
  optionsWrap.append(sizeTitle,optionButtons,sizeGuide);
 }
 if(p.category==='Toys & Games'){
  const age=document.createElement('p');age.className='size-guide';
  age.textContent='Age/safety warning: manufacturer rating not verified. Adults should check packaging before purchase.';
  optionsWrap.append(age);
 }
 const deliveryBox=document.createElement('div');deliveryBox.className='quick-delivery-check';
 const deliveryLabel=document.createElement('label');deliveryLabel.textContent='CHECK DELIVERY PIN';
 const deliveryPin=document.createElement('input');deliveryPin.type='text';deliveryPin.inputMode='numeric';
 deliveryPin.maxLength=6;deliveryPin.placeholder='560001';deliveryPin.setAttribute('aria-label','Delivery PIN code');
 const deliveryButton=document.createElement('button');deliveryButton.type='button';deliveryButton.textContent='CHECK';
 const deliveryMessage=document.createElement('p');deliveryMessage.setAttribute('aria-live','polite');
 let deliverySequence=0;
 deliveryButton.onclick=async()=>{
  const pin=deliveryPin.value.trim(),sequence=++deliverySequence;
  if(!/^[1-9][0-9]{5}$/.test(pin)){deliveryMessage.textContent='Enter a valid six-digit Indian PIN.';return}
  deliveryMessage.textContent='Checking delivery coverage…';
  try{
   const r=await request('/api/store/delivery/check?productId='+encodeURIComponent(selected.productId)+'&pincode='+encodeURIComponent(pin));
   if(sequence!==deliverySequence)return;
   deliveryMessage.textContent=r.status==='SERVICEABLE'?
    'Delivery configured · estimated '+r.minDays+'–'+r.maxDays+' days (seller estimate)':
    r.status==='UNAVAILABLE'?'Not deliverable to '+pin+' from this seller.':
    'Delivery coverage has not been verified for this seller.';
  }catch(e){if(sequence===deliverySequence)deliveryMessage.textContent='Delivery check error: '+e.message}
 };
 deliveryBox.append(deliveryLabel,deliveryPin,deliveryButton,deliveryMessage);
 const details=document.createElement('p');details.className='size-guide';
 details.textContent='Item stock and price are verified by Spring Boot at checkout. Photos are representative.';
 add.onclick=async()=>{
  const picked=selected;
  await addItem(picked.productId);
 };
 const save=document.createElement('button');save.type='button';save.className='outline-button';save.textContent='♡ SAVE TO WISHLIST';
 save.onclick=async()=>{await saveToWishlist(selected.productId)};
 buy.onclick=async()=>{if(await changeQty(selected.productId,1))await checkout()};
 const reviews=document.createElement('button');reviews.type='button';reviews.className='outline-button';
 reviews.textContent='READ / WRITE REVIEWS';reviews.onclick=()=>showReviews(selected.productId);
 const similar=document.createElement('button');similar.type='button';similar.className='outline-button';
 similar.textContent='SIMILAR PICKS ↓';similar.onclick=()=>related.scrollIntoView({behavior:'smooth'});
 info.append(heading,desc,soldBy,optionsWrap,stock,price,deliveryBox,details,add,buy,save,reviews,similar);panel.append(picture,info);root.append(panel);
 const related=document.createElement('section');related.className='pdp-related';
 const relatedTitle=document.createElement('h2');relatedTitle.textContent='SIMILAR PRODUCTS';related.append(relatedTitle);
 const grid=document.createElement('div');grid.className='pdp-grid';related.append(grid);root.append(related);
 const key=x=>String(x.variantGroup||x.catalogProductId||x.productId);
 const seen=new Set([key(p)]);
 function displayRelated(items){
  grid.replaceChildren();
  for(const candidate of items){
   const x=productById(candidate.productId);
   if(!x||x.category!==p.category||x.stock<=0||seen.has(key(x)))continue;
   seen.add(key(x));
   const a=document.createElement('a');a.className='pdp-card';a.href='#product/'+x.productId;
   const photo=document.createElement('div');photo.className='pdp-card-photo';
   if(canShowProductPhoto(x)){const img=document.createElement('img');img.src=x.image;img.alt=x.name;img.loading='lazy';img.onerror=()=>{photo.textContent=chooseIcon(x)};photo.append(img)}else photo.textContent=chooseIcon(x);
   const name=document.createElement('strong');name.textContent=x.variantGroup||x.name;
   const amount=document.createElement('span');amount.textContent=money(itemPrice(x));
   a.append(photo,name,amount);grid.append(a);if(grid.children.length>=12)break;
  }
  if(!grid.children.length)grid.textContent='No similar in-stock products available.';
 }
 grid.textContent='Loading similar products…';
 request('/api/store/recommendations?productId='+encodeURIComponent(id)).then(items=>{
  if(version===detailVersion)displayRelated(Array.isArray(items)?items:[]);
 }).catch(()=>{if(version===detailVersion)displayRelated(getCatalog().filter(x=>x.category===p.category).sort((a,b)=>Number(b.brand===p.brand)-Number(a.brand===p.brand)))});
 renderSelection();
}

return {routeProduct,showQuickView};
};
