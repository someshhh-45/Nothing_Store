(() => {
'use strict';
const $=id=>document.getElementById(id);
const money=n=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(Number(n)||0);
// All authenticated API traffic goes through this storefront's Nginx /api proxy.
const API='';
let catalog=[],visible=[],category='All',searchText='',user=null,cart={},cartBusy=false,retailerStatus='NOT_APPLIED',retailerDiscount=0,searchRequestSeq=0,searchDebounce,promoQuote=null,offerList=[],promoRequestSeq=0;
const productById=id=>catalog.find(p=>String(p.productId)===String(id));
const itemPrice=p=>retailerStatus==='APPROVED'?Math.round((p.price*(100-retailerDiscount)/100)*100)/100:p.price;
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icons={'Electronics':'🎧','Gadgets':'🔌','Toys & Games':'🧸','Fashion':'👟','Home & Living':'💡','Grocery':'🛒','Beauty':'🧴','Coffee':'☕'};
const tones={'Electronics':'#e4eae2','Gadgets':'#dce5dd','Toys & Games':'#e8f3d7','Fashion':'#e9e9df','Home & Living':'#f0ebd8','Grocery':'#ecf6db','Beauty':'#ffecf2'};
// A recognisable, product-specific illustration is preferable to an unrelated stock photo.
const productIcons=[
 [/earbuds/i,'🎧'],[/headphones/i,'🎧'],[/speaker/i,'🔊'],[/charger/i,'🔌'],[/power bank/i,'🔋'],[/keyboard/i,'⌨️'],[/laptop stand/i,'💻'],[/webcam/i,'📷'],[/usb-c multiport/i,'🔗'],[/charging pad/i,'⚡'],[/ssd/i,'💾'],[/watch/i,'⌚'],
 [/water bottle/i,'🧴'],[/bedsheet/i,'🛏️'],[/desk lamp/i,'💡'],[/mug/i,'☕'],[/frying pan/i,'🍳'],[/diffuser/i,'🪔'],[/cushion/i,'🛋️'],[/storage box/i,'🗃️'],[/kitchen scale/i,'⚖️'],
 [/backpack/i,'🎒'],[/t-shirt/i,'👕'],[/running shoes/i,'👟'],[/hoodie/i,'🧥'],[/wallet/i,'👛'],[/sunglasses/i,'🕶️'],[/duffel/i,'🧳'],[/socks/i,'🧦'],[/denim jacket/i,'👖'],
 [/rice/i,'🍚'],[/almonds/i,'🥜'],[/green tea/i,'🍵'],[/groundnut oil/i,'🫗'],[/peanut butter/i,'🥜'],[/oats/i,'🥣'],[/coffee beans/i,'🫘'],[/chocolate/i,'🍫'],
 [/sunscreen/i,'☀️'],[/face wash/i,'🫧'],[/vitamin c serum/i,'💧'],[/moisturizer/i,'🧴'],[/shampoo/i,'🧼'],[/lip balm/i,'💄'],[/hand cream/i,'🖐️'],[/body lotion/i,'🧴']
];
const chooseIcon=p=>productIcons.find(([pattern])=>pattern.test(p.name))?.[1]||icons[p.category]||'📦';
const productArtwork=p=>{
 const palette=['#e9edff','#feede5','#f2eeda','#dff3ed','#ffe7ee','#e9e4fa','#e9f2ff','#f1ecd8'];
 const hash=Array.from(p.name).reduce((x,c)=>(x*31+c.charCodeAt(0))>>>0,7);
 const bg=palette[hash%palette.length];
 return '<div class="product-placeholder" style="--photo-bg:'+bg+'"><span class="product-placeholder-icon" aria-hidden="true">'+chooseIcon(p)+'</span><span class="product-placeholder-name">'+escape(p.name)+'</span><span class="product-placeholder-label">PRODUCT ILLUSTRATION</span></div>';
};
const bindImageFallbacks=scope=>{
 scope.querySelectorAll('img[data-product-image]').forEach(img=>{
  img.addEventListener('error',()=>{
   const p=productById(img.dataset.productImage);
   if(p)img.replaceWith(document.createRange().createContextualFragment(productArtwork(p)));
  },{once:true});
 });
};

const notify=(msg,bad=false,action=false)=>{
 const el=$('toast');el.replaceChildren();
 const label=document.createElement('span');label.textContent=msg;el.append(label);
 if(action){
  const button=document.createElement('button');button.type='button';button.className='toast-action';
  button.textContent='VIEW BAG ↗';button.onclick=()=>{openCart();el.classList.remove('visible')};
  el.append(button);
 }
 el.style.background=bad?'#922f2f':'#172b19';
 el.classList.add('visible');clearTimeout(notify.timer);
 notify.timer=setTimeout(()=>el.classList.remove('visible'),5000);
};
async function request(path,options={}){
 const r=await fetch(API+path,{...options,credentials:'include',headers:{'Content-Type':'application/json',...(options.headers||{})}});
 const raw=await r.text();let data;try{data=JSON.parse(raw)}catch{data=raw}
 if(!r.ok)throw new Error((data&&typeof data==='object'&&(data.message||data.error))||('HTTP '+r.status));
 return data;
}
function saveGuest(){localStorage.setItem('nothing-guest-cart',JSON.stringify(cart))}
function readGuest(){try{return JSON.parse(localStorage.getItem('nothing-guest-cart')||'{}')||{}}catch{return {}}}
function updateCart(){
 const items=Object.entries(cart).filter(([id,q])=>Number(q)>0&&productById(id));
 const count=items.reduce((n,[id,q])=>n+Number(q),0);
 $('cartCount').textContent=count;
 const rawSubtotal=items.reduce((n,[id,q])=>n+itemPrice(productById(id))*q,0);
 $('cartTotal').textContent=money(rawSubtotal);
 renderPromoQuote(rawSubtotal);
 $('cartItems').innerHTML=items.length?items.map(([id,q])=>{
   const p=productById(id);const thumb=p.image&&/^https:\/\//.test(p.image)?'<img data-product-image="'+escape(id)+'" loading="lazy" src="'+escape(p.image)+'" alt="'+escape(p.name)+'">':chooseIcon(p);return '<div class="cart-row"><div class="cart-icon">'+thumb+'</div><div><strong>'+escape(p.name)+'</strong><small>'+escape(p.variantLabel?('SIZE '+p.variantLabel+' · '):'')+escape(p.category)+' · '+escape(p.city||'India')+'</small><div class="qty"><button type="button" data-qty="'+id+'" data-delta="-1" aria-label="Decrease quantity">−</button><b>'+q+'</b><button type="button" data-qty="'+id+'" data-delta="1" aria-label="Increase quantity">+</button></div></div><div class="cart-price">'+money(itemPrice(p)*q)+'</div></div>';
 }).join(''):'<div class="empty-state"><strong>YOUR BAG IS EMPTY.</strong><p>Some good stuff is waiting for you.</p></div>';
 $('cartSyncStatus').textContent=user?'Saved to your PostgreSQL cart':'Guest bag saved on this device';
 $('checkoutBtn').disabled=!count||cartBusy;
 bindImageFallbacks($('cartItems'));
 document.querySelectorAll('[data-qty]').forEach(el=>el.onclick=()=>changeQty(el.dataset.qty,Number(el.dataset.delta)));
}
async function persistCart(){
 if(!user){saveGuest();updateCart();return}
 cartBusy=true;updateCart();
 try{
  const items=Object.entries(cart).filter(([id,q])=>Number(q)>0).map(([id,quantity])=>({productId:Number(id),quantity:Number(quantity)}));
  await request('/api/cart/create',{method:'POST',body:JSON.stringify(items)});
  await syncPromoQuote();
 }catch(e){notify('Cart could not sync: '+e.message,true);throw e}
 finally{cartBusy=false;updateCart()}
}
async function changeQty(id,delta){
 const p=productById(id);if(!p||cartBusy)return;
 const next=Math.max(0,Math.min(99,Number(cart[id]||0)+delta));
 if(next>p.stock){notify('Only '+p.stock+' available right now',true);return}
 const before={...cart};
 promoQuote=null;promoRequestSeq++;
 if(next)cart[id]=next;else delete cart[id];
 updateCart();
 try{
  await persistCart();
  if(delta>0&&next>0) notify('Added '+p.name+(p.variantLabel?' / '+p.variantLabel:'')+' to your bag',false,true);
  if(delta<0) notify(next===0?'Removed item from your bag':'Bag updated');
 }catch{cart=before;updateCart()}
}
async function addItem(id){await changeQty(id,1)}
function openCart(){$('overlay').hidden=false;$('cartDrawer').classList.add('open');$('cartDrawer').setAttribute('aria-hidden','false')}
function closeCart(){$('overlay').hidden=true;$('cartDrawer').classList.remove('open');$('cartDrawer').setAttribute('aria-hidden','true')}
function productVariants(p){
 if(!p.variantGroup)return [p];
 const options=catalog.filter(x=>x.variantGroup===p.variantGroup);
 const rank=x=>x.sizeType==='SHOE'?Number(String(x.variantLabel).replace('UK ','')) : ['XS','S','M','L','XL','XXL'].indexOf(x.variantLabel);
 return options.sort((a,b)=>rank(a)-rank(b));
}
function showQuickView(id){
 const p=productById(id);if(!p)return;
 const variants=productVariants(p);let selected=variants.find(v=>v.stock>0)||variants[0];
 const dialog=$('modal'),root=$('modalContent');root.replaceChildren();
 const title=document.createElement('h2');title.textContent='THE DETAIL / '+(p.brand||'NOTHING SELECT');root.append(title);
 const panel=document.createElement('div');panel.className='quick-panel';
 const picture=document.createElement('div');picture.className='quick-photo';
 if(p.image&&/^https:\/\//.test(p.image)){
  const img=document.createElement('img');img.src=p.image;img.alt='Illustrative product image: '+(p.variantGroup||p.name);
  img.onerror=()=>{picture.textContent=chooseIcon(p)};picture.append(img);
 }else picture.textContent=chooseIcon(p);
 const info=document.createElement('div');info.className='quick-info';
 const heading=document.createElement('h3');heading.textContent=p.variantGroup||p.name;
 const desc=document.createElement('p');desc.textContent=p.description;
 const stock=document.createElement('p');stock.className='quick-stock';
 const price=document.createElement('strong');price.className='quick-price';
 const optionsWrap=document.createElement('div');optionsWrap.className='size-picker';
 const sizeTitle=document.createElement('div');sizeTitle.className='size-picker-head';
 const sizeGuide=document.createElement('p');sizeGuide.className='size-guide';
 const optionButtons=document.createElement('div');optionButtons.className='size-options';
 const add=document.createElement('button');add.className='primary-choice';
 const close=document.createElement('button');close.className='outline-button';close.textContent='CLOSE';close.onclick=()=>dialog.close();
 function renderSelection(){
  price.textContent=money(itemPrice(selected));
  stock.textContent=selected.city+' · '+selected.stock+' in stock'+(selected.footLengthCm?' · approx. foot length '+selected.footLengthCm+' cm':'');
  add.textContent=selected.stock>0?'ADD '+selected.variantLabel?.toUpperCase?.()+' TO BAG ↗':'SOLD OUT';
  if(!selected.variantLabel)add.textContent=selected.stock>0?'ADD TO BAG ↗':'SOLD OUT';
  add.disabled=selected.stock<=0;
  for(const b of optionButtons.querySelectorAll('button')){
   const v=variants.find(x=>String(x.productId)===b.dataset.variant);
   b.classList.toggle('selected',v===selected);
   b.setAttribute('aria-pressed',String(v===selected));
  }
 }
 if(variants.length>1){
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
 const details=document.createElement('p');details.className='size-guide';
 details.textContent='Item stock and price are verified by Spring Boot at checkout. Photos are representative.';
 add.onclick=async()=>{
  const picked=selected;
  dialog.close();
  await addItem(picked.productId);
 };
 const save=document.createElement('button');save.type='button';save.className='outline-button';save.textContent='♡ SAVE TO WISHLIST';
 save.onclick=async()=>{const id=selected.productId;dialog.close();await saveToWishlist(id)};
 const reviews=document.createElement('button');reviews.type='button';reviews.className='outline-button';
 reviews.textContent='READ / WRITE REVIEWS';reviews.onclick=()=>{dialog.close();showReviews(selected.productId)};
 const similar=document.createElement('button');similar.type='button';similar.className='outline-button';
 similar.textContent='SIMILAR PICKS ↗';similar.onclick=()=>{dialog.close();showRecommendations(selected.productId)};
 info.append(heading,desc,optionsWrap,stock,price,details,add,save,reviews,similar,close);panel.append(picture,info);root.append(panel);
 renderSelection();dialog.showModal();
}
function drawProducts(){
 // Each variant has its own stock-bearing Product ID, but shoppers see one card per design.
 const byGroup=new Map();
 for(const p of visible){const key=p.variantGroup||String(p.productId);
  if(!byGroup.has(key))byGroup.set(key,p);
  else if(Number(byGroup.get(key).stock)<=0&&Number(p.stock)>0)byGroup.set(key,p);
 }
 const shown=[...byGroup.values()];
 $('resultCount').textContent=shown.length+' ITEM'+(shown.length===1?'':'S')+' IN THIS EDIT';
 $('emptyState').hidden=shown.length>0;
 $('productGrid').innerHTML=shown.map(p=>{
  const variants=productVariants(p),sized=variants.length>1,stock=variants.reduce((n,v)=>n+Number(v.stock||0),0),safeId=Number(p.productId);
  let image=productArtwork(p);
  if(p.image&&/^https:\/\//.test(p.image))image='<img data-product-image="'+safeId+'" loading="lazy" decoding="async" src="'+escape(p.image)+'" alt="'+escape(p.variantGroup||p.name)+' — illustrative photography">';
  const action=stock<1?'SOLD OUT':sized?'CHOOSE SIZE ↗':'ADD TO BAG +';
  return '<article class="product-card"><div class="product-visual" style="--tone:'+(tones[p.category]||'#e7f0ed')+'"><span class="tag">'+(stock>0?'● IN STOCK':'SOLD OUT')+'</span>'+image+
   '<button type="button" class="quick-view" data-quick="'+safeId+'" aria-label="Quick view '+escape(p.variantGroup||p.name)+'">QUICK VIEW ↗</button></div><div class="product-info"><button type="button" class="save-button" data-save="'+safeId+'" aria-label="Save to wishlist">♡ SAVE</button><div class="brand-tag">'+escape(p.brand||'NOTHING SELECT')+'</div><div class="meta"><span>'+escape(p.category)+'</span><span>⌖ '+escape(p.city||'India')+'</span></div><h3>'+escape(p.variantGroup||p.name)+'</h3><p class="description">'+escape(p.description)+'</p><div class="product-bottom"><strong class="price">'+money(itemPrice(p))+'</strong><button class="add-button" data-add="'+safeId+'" '+(stock<1?'disabled':'')+'>'+action+'</button></div></div></article>';
 }).join('');
 bindImageFallbacks($('productGrid'));
 document.querySelectorAll('[data-add]').forEach(b=>b.onclick=()=>{
  const p=productById(b.dataset.add);if(p&&productVariants(p).length>1)showQuickView(p.productId);
  else addItem(b.dataset.add);
 });
 document.querySelectorAll('[data-quick]').forEach(b=>b.onclick=()=>showQuickView(b.dataset.quick));
 document.querySelectorAll('[data-save]').forEach(b=>b.onclick=()=>saveToWishlist(b.dataset.save));
}
async function applyFilters(){
 const sequence=++searchRequestSeq;
 if(!catalog.length){
  $('sourceStatus').textContent='● CATALOG NOT READY';
  $('resultCount').textContent='WAITING FOR CATALOG';
  $('emptyState').hidden=true;
  return;
 }
 let products=catalog.slice();
 const city=$('cityFilter').value,range=$('priceFilter').value,sort=$('sortFilter').value,brand=$('brandFilter').value;
 const [min,max]=range?range.split('-').map(Number):[null,null];
 if(searchText||category!=='All'||city||range||brand){
  const params=new URLSearchParams({q:searchText,size:'100'});
  if(category!=='All')params.set('category',category);
  if(city)params.set('city',city);
  if(range){params.set('minPrice',String(min));params.set('maxPrice',String(max))}
  $('sourceStatus').textContent='● SEARCHING OPENSEARCH…';
  try{
   const ids=new Set();let page=0,total=Infinity;
   while(page<10&&page*100<total){
    params.set('page',String(page));
    const data=await request('/api/search/products?'+params);
    if(sequence!==searchRequestSeq)return;
    const hits=data.hits?.hits||[];
    for(const hit of hits)ids.add(String(hit._source?.productId));
    total=Number(data.hits?.total?.value||0);
    if(!hits.length)break;
    page++;
   }
   products=catalog.filter(p=>ids.has(String(p.productId)));
   $('sourceStatus').textContent='● OPENSEARCH INDEX';
  }catch(e){
   if(sequence!==searchRequestSeq)return;
   notify('Search is temporarily unavailable: '+e.message,true);
   products=[];$('sourceStatus').textContent='● SEARCH UNAVAILABLE';
  }
 }else $('sourceStatus').textContent='● LIVE POSTGRESQL INVENTORY';
 if(sequence!==searchRequestSeq)return;
 if(brand)products=products.filter(p=>(p.brand||'NOTHING SELECT')===brand);
 if(sort==='price-asc')products.sort((a,b)=>itemPrice(a)-itemPrice(b));
 if(sort==='price-desc')products.sort((a,b)=>itemPrice(b)-itemPrice(a));
 if(sort==='name')products.sort((a,b)=>a.name.localeCompare(b.name));
 visible=products;drawProducts();
}
function setCategory(name){
 category=name;
 document.querySelectorAll('[data-category]').forEach(x=>x.classList.toggle('active',x.dataset.category===name));
 document.querySelector('#catalog').scrollIntoView({behavior:'smooth'});
 applyFilters();
}
function setupFilters(){
 const categories=['All',...new Set(catalog.map(p=>p.category))];
 $('categoryChips').innerHTML=categories.map(c=>'<button class="chip '+(c===category?'active':'')+'" data-category="'+escape(c)+'">'+escape(c).toUpperCase()+'</button>').join('');
 document.querySelectorAll('[data-category]').forEach(x=>x.onclick=()=>setCategory(x.dataset.category));
 const brands=[...new Set(catalog.map(p=>p.brand||'NOTHING SELECT'))].sort();
 $('brandFilter').innerHTML='<option value="">All brands</option>'+brands.map(b=>'<option value="'+escape(b)+'">'+escape(b)+'</option>').join('');
 const cities=[...new Set(catalog.map(p=>p.city).filter(Boolean))].sort();
 $('cityFilter').innerHTML='<option value="">All India</option>'+cities.map(c=>'<option value="'+escape(c)+'">'+escape(c)+'</option>').join('');
}
async function loadCatalog(attempt=0){
 try{
  $('sourceStatus').textContent='● CONNECTING TO SPRING BOOT…';
  const data=await request('/api/store/catalog');
  if(!Array.isArray(data))throw new Error('Invalid catalog response');
  catalog=data.filter(p=>p&&p.productId!=null).map(p=>({...p,price:Number(p.price),stock:Number(p.stock)}));
  if(!catalog.length){
   // An empty 200 response during Java startup must not look like a valid 0-result search.
   if(attempt<6){
    $('productGrid').innerHTML='<div class="loading-card">Spring Boot is starting and loading demo products… (retry '+(attempt+1)+'/6)</div>';
    $('sourceStatus').textContent='● WAITING FOR CATALOG SEED';
    await new Promise(resolve=>setTimeout(resolve,1800));
    return loadCatalog(attempt+1);
   }
   $('sourceStatus').textContent='● CATALOG EMPTY · NEEDS ATTENTION';
   $('resultCount').textContent='NO PRODUCT RECORDS RETURNED';
   $('emptyState').hidden=true;
   $('productGrid').innerHTML='<div class="loading-card"><strong>THE CATALOG IS NOT READY.</strong><p>The API returned zero products. This is a database/seed issue, not a search filter. From the project root run: <code>bash scripts/repair-marketplace.sh</code></p><button type="button" id="retryCatalog" class="outline-button">RETRY CATALOG ↻</button></div>';
   $('retryCatalog').onclick=()=>loadCatalog(0);
   return;
  }
  setupFilters();
  if($('heroProductCount'))$('heroProductCount').textContent=catalog.length+'+';
  await applyFilters();updateCart();
 }catch(e){
  if(attempt<4){
   $('sourceStatus').textContent='● WAITING FOR API…';
   $('productGrid').innerHTML='<div class="loading-card">Spring Boot API initializing… retry '+(attempt+1)+'/4</div>';
   await new Promise(resolve=>setTimeout(resolve,1800));
   return loadCatalog(attempt+1);
  }
  $('sourceStatus').textContent='● BACKEND UNAVAILABLE';
  $('emptyState').hidden=true;
  $('productGrid').innerHTML='<div class="loading-card"><strong>BACKEND CONNECTION FAILED.</strong><p>'+escape(e.message)+'</p><button id="retryCatalog" type="button" class="outline-button">TRY AGAIN ↻</button></div>';
  $('retryCatalog').onclick=()=>loadCatalog(0);
  notify('Cannot load Spring Boot catalog. Check Docker logs.',true);
 }
}
async function loadServerCart(){
 if(!user){cart=readGuest();updateCart();return}
 try{
  const carts=await request('/api/carts');
  const server={};
  for(const c of (Array.isArray(carts)?carts:[])){
    for(const p of (c.products||[]))if(p.productId!=null&&p.quantity>0)server[p.productId]=p.quantity;
  }
  const guest=readGuest();cart=server;
  if(Object.keys(guest).length){
    for(const [id,q] of Object.entries(guest))cart[id]=Math.min(99,Number(cart[id]||0)+Number(q||0));
    await persistCart();localStorage.removeItem('nothing-guest-cart');
  }
  updateCart();await syncPromoQuote();
 }catch(e){cart={};promoQuote=null;updateCart();notify('Could not restore your saved cart: '+e.message,true)}
}
async function loadRetailerStatus(){
 retailerStatus='NOT_APPLIED';retailerDiscount=0;
 if(!user)return;
 try{
  const data=await request('/api/retailer/me');
  retailerStatus=data.status||'NOT_APPLIED';
  retailerDiscount=data.status==='APPROVED'?Number(data.discountPercent||0):0;
 }catch(e){notify('Retailer status unavailable: '+e.message,true)}
}
async function verifySession(){
 try{
  // A real authenticated response includes an account ID and username.
  // /api/auth/username is public and must not be used as proof of login.
  const session=await request('/api/auth/user');
  if(!session||!session.id||!session.username||session.username==='anonymousUser'){
   user=null;return null;
  }
  user=session.username;
  return session;
 }catch(e){user=null;return null}
}
async function loadSession(){
 const session=await verifySession();
 syncAccountButtons();
 $('adminRetailersBtn').hidden=!(session?.roles||[]).includes('ROLE_ADMIN');
 $('adminOrdersBtn').hidden=!(session?.roles||[]).includes('ROLE_ADMIN');
 $('adminRequestsBtn').hidden=!(session?.roles||[]).includes('ROLE_ADMIN');
 $('adminInventoryBtn').hidden=!(session?.roles||[]).includes('ROLE_ADMIN');
 $('adminOffersBtn').hidden=!(session?.roles||[]).includes('ROLE_ADMIN');
 $('sellerInventoryBtn').hidden=!(session?.roles||[]).includes('ROLE_SELLER');
 await loadRetailerStatus();await applyFilters();await loadServerCart();
}
function showDialog(title,fields,button='CONTINUE'){
 return new Promise(resolve=>{
  const dialog=$('modal'),root=$('modalContent');root.replaceChildren();
  const h=document.createElement('h2');h.textContent=title;root.appendChild(h);
  const form=document.createElement('form'),inputs={};
  let consentedCoordinates=null;
  for(const f of fields){
   const label=document.createElement('label');label.textContent=f.label;const input=document.createElement('input');
   input.name=f.name;input.required=f.required!==false;input.type=f.type||'text';input.placeholder=f.placeholder||'';input.autocomplete=f.autocomplete||'off';
   if(f.minLength)input.minLength=f.minLength;
   label.append(input);form.append(label);inputs[f.name]=input;
  }
  if(title==='DELIVERY ADDRESS'){
   const locate=document.createElement('button');locate.type='button';locate.className='outline-button';
   locate.textContent='⌖ USE CURRENT LOCATION (OPTIONAL)';
   const detail=document.createElement('p');detail.className='size-guide';
   detail.textContent='Your browser will request permission. Enter the full delivery address even when coordinates are captured.';
   locate.onclick=()=>{
    if(!navigator.geolocation){detail.textContent='Location unavailable in this browser.';return}
    locate.disabled=true;
    navigator.geolocation.getCurrentPosition(
     pos=>{consentedCoordinates={latitude:pos.coords.latitude,longitude:pos.coords.longitude};
      detail.textContent='Location pin captured. Please complete the address fields.';locate.disabled=false},
     ()=>{detail.textContent='Location permission denied or unavailable. Enter the address manually.';locate.disabled=false},
     {enableHighAccuracy:false,timeout:12000,maximumAge:0});
   };
   form.append(locate,detail);
  }
  const actions=document.createElement('div');actions.className='actions';
  const cancel=document.createElement('button');cancel.type='button';cancel.textContent='CANCEL';
  const submit=document.createElement('button');submit.type='submit';submit.textContent=button;actions.append(cancel,submit);form.append(actions);root.append(form);
  let finished=false;const end=value=>{if(finished)return;finished=true;dialog.close();resolve(value)};
  cancel.onclick=()=>end(null);dialog.oncancel=e=>{e.preventDefault();end(null)};
  form.onsubmit=e=>{e.preventDefault();const data={};for(const [name,input] of Object.entries(inputs))data[name]=input.value.trim();if(consentedCoordinates)Object.assign(data,consentedCoordinates);end(data)};
  dialog.showModal();Object.values(inputs)[0]?.focus();
 });
}
function selectAccountMode(){
 return new Promise(resolve=>{
  const dialog=$('modal'),root=$('modalContent');root.replaceChildren();
  const title=document.createElement('h2');title.textContent='YOUR NOTHING ACCOUNT';root.append(title);
  const p=document.createElement('p');p.textContent='Choose how you want to continue.';root.append(p);
  const actions=document.createElement('div');actions.className='account-choices';root.append(actions);
  let finished=false;const end=value=>{if(finished)return;finished=true;dialog.close();resolve(value)};
  for(const [value,label] of [['LOGIN','SIGN IN'],['SIGNUP','CREATE ACCOUNT']]){
   const button=document.createElement('button');button.className='primary-choice';button.textContent=label;
   button.onclick=()=>end(value);actions.append(button);
  }
  dialog.oncancel=e=>{e.preventDefault();end(null)};
  dialog.showModal();
 });
}
async function login(mode=null){
 const choice=mode||await selectAccountMode();
 if(!choice)return false;
 const signup=choice==='SIGNUP';
 const fields=[{name:'username',label:'USERNAME',autocomplete:'username',minLength:3}];
 if(signup)fields.push({name:'email',label:'EMAIL ADDRESS',type:'email',autocomplete:'email'});
 fields.push({name:'password',label:'PASSWORD',type:'password',autocomplete:signup?'new-password':'current-password',minLength:6});
 const data=await showDialog(signup?'CREATE YOUR ACCOUNT':'WELCOME BACK',fields,signup?'SIGN UP':'SIGN IN');
 if(!data)return false;
 try{
  if(signup){
   await request('/api/auth/signup',{method:'POST',body:JSON.stringify(data)});
   const otp=await showDialog('VERIFY YOUR EMAIL',[
     {name:'code',label:'SIX-DIGIT CODE SENT TO '+data.email,type:'text',placeholder:'123456',minLength:6}
   ],'VERIFY EMAIL');
   if(!otp)return false;
   if(!/^[0-9]{6}$/.test(otp.code))throw new Error('Enter the six-digit code from your email.');
   await request('/api/auth/verify-email',{method:'POST',
     body:JSON.stringify({username:data.username,code:otp.code})});
  }
  await request('/api/auth/signin',{method:'POST',body:JSON.stringify({username:data.username,password:data.password})});
  const session=await verifySession();
  if(!session)throw new Error('Sign-in cookie was not accepted. Use http://localhost:8000 and enable site cookies.');
  syncAccountButtons();
  $('adminRetailersBtn').hidden=!(session.roles||[]).includes('ROLE_ADMIN');
  $('adminOrdersBtn').hidden=!(session.roles||[]).includes('ROLE_ADMIN');
  $('adminRequestsBtn').hidden=!(session.roles||[]).includes('ROLE_ADMIN');
  $('adminInventoryBtn').hidden=!(session.roles||[]).includes('ROLE_ADMIN');
  $('adminOffersBtn').hidden=!(session.roles||[]).includes('ROLE_ADMIN');
  $('sellerInventoryBtn').hidden=!(session.roles||[]).includes('ROLE_SELLER');
  await loadRetailerStatus();await applyFilters();await loadServerCart();notify('Welcome, '+user+'! Your cart is synced.');
  return true;
 }catch(e){notify('Account error: '+e.message,true);return false}
}
function syncAccountButtons(){
 $('signInBtn').hidden=!!user;
 $('registerBtn').hidden=!!user;
 $('accountBtn').hidden=!user;
 $('logoutBtn').hidden=!user;
 $('accountLabel').textContent=user?user.toUpperCase():'ACCOUNT';
}
async function logout(){
 try{await request('/api/auth/signout',{method:'POST'})}catch(e){console.warn('Sign-out request:',e)}
 user=null;cart={};retailerStatus='NOT_APPLIED';retailerDiscount=0;
 $('adminRetailersBtn').hidden=true;
 $('adminOrdersBtn').hidden=true;
 $('adminRequestsBtn').hidden=true;
 $('adminInventoryBtn').hidden=true;
 $('adminOffersBtn').hidden=true;
 promoQuote=null;promoRequestSeq++;
 $('sellerInventoryBtn').hidden=true;
 // Do not transfer one customer's bag to the next shopper on the same device.
 localStorage.removeItem('nothing-guest-cart');
 syncAccountButtons();await applyFilters();updateCart();
 notify('Signed out. Your saved account bag remains in PostgreSQL.');
}
async function accountAction(){
 if(!user){await login('LOGIN');return}
 await informationModal('YOUR ACCOUNT','Signed in as '+user+'. Use ORDERS to track purchases, or the SIGN OUT button in the header.');
}
async function retailerAction(){
 if(!user && !await login())return;
 try{
  await loadRetailerStatus();
  if(retailerStatus==='APPROVED'){
   const result=await showDialog('RETAILER ACCOUNT APPROVED',[{name:'status',label:'APPROVED · '+retailerDiscount+'% DISCOUNT ON ALL STORE ITEMS',placeholder:'THANK YOU'}],'CLOSE');
   return;
  }
  if(retailerStatus==='PENDING'){
   await informationModal('VERIFICATION PENDING','Your GSTIN is submitted. A store administrator must check it against official GST records before activating retailer prices. No discount is active yet.');
   return;
  }
  const data=await showDialog('APPLY FOR RETAILER PRICING',[
   {name:'businessName',label:'REGISTERED BUSINESS NAME',placeholder:'Your GST registered business',minLength:3},
   {name:'gstin',label:'GSTIN (REQUIRED — 15 CHARACTERS)',placeholder:'29ABCDE1234F1Z5',minLength:15}
  ],'SUBMIT FOR REVIEW');
  if(!data)return;
  const result=await request('/api/retailer/apply',{method:'POST',body:JSON.stringify(data)});
  retailerStatus=result.status;
  await informationModal('APPLICATION SUBMITTED','Your request is now PENDING. An administrator must verify GST registration before retailer discounts begin.');
 }catch(e){notify('Retailer application failed: '+e.message,true)}
}
function informationModal(title,message){
 return new Promise(resolve=>{
  const d=$('modal'),root=$('modalContent');root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent=title;
  const p=document.createElement('p');p.textContent=message;
  const done=document.createElement('button');done.className='outline-button';done.textContent='CLOSE';
  done.onclick=()=>{d.close();resolve()};
  d.oncancel=()=>resolve();root.append(heading,p,done);d.showModal();
 });
}
async function reviewRetailers(){
 try{
  const items=await request('/api/admin/retailers');
  const d=$('modal'),root=$('modalContent');root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='RETAILER APPROVAL QUEUE';root.append(heading);
  const help=document.createElement('p');help.textContent='Use gst.gov.in > Search Taxpayer to verify the GSTIN and legal name. Approve only after successful verification.';root.append(help);
  if(!items.length){const empty=document.createElement('p');empty.textContent='No pending retailer applications.';root.append(empty)}
  for(const item of items){
   const block=document.createElement('section');block.className='review-card';
   const name=document.createElement('strong');name.textContent=item.businessName+' · '+item.gstin;block.append(name);
   const row=document.createElement('div');row.className='actions';
   for(const decision of ['APPROVED','REJECTED']){
    const btn=document.createElement('button');btn.type='button';btn.textContent=decision==='APPROVED'?'APPROVE':'REJECT';
    btn.onclick=async()=>{
     try{
      await request('/api/admin/retailers/'+item.id+'/review',{method:'PUT',body:JSON.stringify({decision,note:'Reviewed by store administrator'})});
      notify(item.businessName+' marked '+decision.toLowerCase());d.close();await reviewRetailers();
     }catch(e){notify('Review failed: '+e.message,true)}
    };row.append(btn)
   }
   block.append(row);root.append(block);
  }
  const exit=document.createElement('button');exit.className='outline-button';exit.textContent='CLOSE';exit.onclick=()=>d.close();root.append(exit);d.showModal();
 }catch(e){notify('Approval queue unavailable: '+e.message,true)}
}

function trackingLabel(stage){return String(stage||'').replaceAll('_',' ')}
function formatEventTime(value){
 if(!value)return 'Time not recorded';
 const date=new Date(value);return Number.isNaN(date.getTime())?'Time not recorded':
  new Intl.DateTimeFormat('en-IN',{day:'numeric',month:'short',year:'numeric',hour:'numeric',minute:'2-digit'}).format(date);
}
const stageOptions={
 PLACED:['CONFIRMED','CANCELLED'],
 CONFIRMED:['PACKED','CANCELLED'],
 PACKED:['SHIPPED','CANCELLED'],
 SHIPPED:['OUT_FOR_DELIVERY'],
 OUT_FOR_DELIVERY:['DELIVERED']
};
async function showTracking(orderId,admin=false){
 try{
  const data=await request((admin?'/api/admin/orders/':'/api/customer/orders/')+encodeURIComponent(orderId)+'/tracking');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const title=document.createElement('h2');title.textContent='TRACK ORDER #'+data.orderId;
  const intro=document.createElement('p');intro.className='tracking-disclaimer';
  intro.textContent='These are store-confirmed fulfillment updates. Live courier GPS/scans are not connected.';
  const status=document.createElement('div');status.className='tracking-status';
  const label=document.createElement('span');label.className='eyebrow';label.textContent='CURRENT STAGE';
  const statusText=document.createElement('strong');statusText.textContent=trackingLabel(data.currentStage);
  status.append(label,statusText);
  root.append(title,intro,status);
  if(data.carrier && data.trackingNumber){
    const courier=document.createElement('p');courier.className='tracking-courier';
    courier.textContent='CARRIER: '+data.carrier+' · REFERENCE: '+data.trackingNumber+
      (data.estimatedDeliveryDate?' · MANUAL ESTIMATE: '+data.estimatedDeliveryDate:'');
    root.append(courier);
  }else if(data.estimatedDeliveryDate){
    const estimate=document.createElement('p');estimate.textContent='Staff-estimated delivery: '+data.estimatedDeliveryDate;root.append(estimate);
  }
  const steps=document.createElement('ol');steps.className='tracking-timeline';
  for(const milestone of (data.milestones||[])){
    const li=document.createElement('li');li.className='tracking-milestone'+(milestone.completed?' completed':'');
    if(milestone.stage===data.currentStage)li.classList.add('current');
    const dot=document.createElement('span');dot.className='tracking-dot';dot.textContent=milestone.completed?'✓':'';
    const details=document.createElement('div');const stage=document.createElement('strong');
    stage.textContent=trackingLabel(milestone.stage);
    const time=document.createElement('small');
    time.textContent=milestone.occurredAt?formatEventTime(milestone.occurredAt):
      (milestone.completed?'Status known · date not recorded':'Not yet recorded');
    details.append(stage,time);li.append(dot,details);steps.append(li);
  }
  root.append(steps);
  if((data.history||[]).length){
    const historyTitle=document.createElement('h3');historyTitle.className='tracking-history-heading';
    historyTitle.textContent='EVENT HISTORY';root.append(historyTitle);
    for(const event of data.history){
      const item=document.createElement('p');item.className='tracking-history-item';
      item.textContent=formatEventTime(event.occurredAt)+' · '+trackingLabel(event.stage)+
        (event.note?' · '+event.note:'')+' · '+event.updatedBy;
      root.append(item);
    }
  }
  if(admin){
    const actions=document.createElement('div');actions.className='tracking-actions';
    for(const next of (stageOptions[data.currentStage]||[])){
      const btn=document.createElement('button');btn.className='primary-choice';
      btn.textContent='MARK '+trackingLabel(next)+' ↗';
      btn.onclick=async()=>{
        d.close();
        const fields=[{name:'note',label:'STAFF NOTE FOR THIS MILESTONE',placeholder:'Order verified and processing',minLength:3}];
        if(next==='SHIPPED'){
          fields.push({name:'carrier',label:'COURIER NAME (MANUALLY ENTERED)',placeholder:'Your shipping provider'});
          fields.push({name:'trackingNumber',label:'WAYBILL / TRACKING NUMBER',placeholder:'SHIP123456',minLength:5});
          fields.push({name:'estimatedDeliveryDate',label:'EXPECTED DELIVERY DATE (OPTIONAL)',type:'date',required:false});
        }
        const input=await showDialog('UPDATE FULFILLMENT · '+trackingLabel(next),fields,'SAVE MILESTONE');
        if(!input){await showTracking(orderId,true);return}
        const payload={stage:next,note:input.note};
        if(next==='SHIPPED'){
          payload.carrier=input.carrier;payload.trackingNumber=input.trackingNumber;
          if(input.estimatedDeliveryDate)payload.estimatedDeliveryDate=input.estimatedDeliveryDate;
        }
        try{
          await request('/api/admin/orders/'+orderId+'/tracking',{method:'PUT',body:JSON.stringify(payload)});
          notify('Order #'+orderId+' updated: '+trackingLabel(next));
        }catch(e){notify('Tracking update failed: '+e.message,true)}
        await showTracking(orderId,true);
      };
      actions.append(btn);
    }
    root.append(actions);
  }
  const buttons=document.createElement('div');buttons.className='tracking-actions';
  const back=document.createElement('button');back.className='outline-button';
  back.textContent=admin?'BACK TO FULFILLMENT ↗':'BACK TO ORDERS ↗';
  back.onclick=()=>{d.close();admin?showFulfillmentPanel():showOrders()};
  const close=document.createElement('button');close.className='outline-button';
  close.textContent='CLOSE';close.onclick=()=>d.close();
  buttons.append(back,close);root.append(buttons);
  d.showModal();
 }catch(e){notify('Order tracking unavailable: '+e.message,true)}
}
async function showFulfillmentPanel(){
 try{
  const data=await request('/api/admin/orders?pageNumber=0&pageSize=50&sortBy=orderId&sortOrder=desc');
  const orders=Array.isArray(data.content)?data.content:[];
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const title=document.createElement('h2');title.textContent='ORDER FULFILLMENT';root.append(title);
  const caption=document.createElement('p');caption.textContent='Authorized staff only. Select an order to add a verified store milestone. This does not connect to a courier.';root.append(caption);
  if(!orders.length){const e=document.createElement('p');e.textContent='No customer orders yet.';root.append(e)}
  for(const o of orders){
    const b=document.createElement('button');b.type='button';b.className='address-option fulfillment-row';
    b.textContent='ORDER #'+o.orderId+' · '+(o.orderStatus||'PLACED')+' · '+money(o.totalAmount||0);
    b.onclick=()=>{d.close();showTracking(o.orderId,true)};root.append(b);
  }
  if(data.totalElements>50){
   const note=document.createElement('p');note.textContent='Showing the latest 50 orders. Use the admin API for older orders.';root.append(note);
  }
  const close=document.createElement('button');close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Staff order dashboard unavailable: '+e.message,true)}
}
async function showOrders(){
 if(!user){if(!await login())return}
 try{
  const data=await request('/api/customer/orders');
  const orders=Array.isArray(data)?data:[];
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const title=document.createElement('h2');title.textContent='YOUR ORDERS';root.append(title);
  const requestsButton=document.createElement('button');requestsButton.type='button';
  requestsButton.className='outline-button';requestsButton.textContent='MY RETURN / DELIVERY REQUESTS ↗';
  requestsButton.onclick=()=>{d.close();showMyRequests()};root.append(requestsButton);
  if(!orders.length){const p=document.createElement('p');p.textContent='No orders yet. Your next favorite find awaits!';root.append(p)}
  for(const o of orders){
   const row=document.createElement('div');row.className='tracking-order-card';
   const h=document.createElement('strong');h.textContent='ORDER #'+o.orderId+' · '+money(o.totalAmount);
   const p=document.createElement('p');p.textContent=(o.orderDate||'')+' · '+(o.orderStatus||'Placed');
   row.append(h,p);
   if(Number(o.discountAmount)>0){
    const saving=document.createElement('p');saving.className='order-offer-saved';
    saving.textContent='OFFER '+(o.offerCode||'')+' · YOU SAVED '+money(o.discountAmount);
    row.append(saving);
   }
   for(const item of (Array.isArray(o.orderItems)?o.orderItems:[])){
    const line=document.createElement('div');line.className='order-detail-line';
    const product=item.product||{};
    line.textContent=(Number(item.quantity)||0)+' × '+(product.productName||'Product')+
      (product.variantLabel?' · size '+product.variantLabel:'')+' · '+money(item.orderedProductPrice);
    row.append(line);
   }
   const track=document.createElement('button');track.type='button';track.className='track-order-button';
   track.textContent='TRACK YOUR ORDER ↗';track.onclick=()=>{d.close();showTracking(o.orderId)};
   row.append(track);
   const reschedule=document.createElement('button');reschedule.type='button';reschedule.className='track-order-button';
   reschedule.textContent='REQUEST DELIVERY DATE ↗';reschedule.onclick=()=>{d.close();newAfterSaleRequest(o.orderId,'RESCHEDULE')};
   const returned=document.createElement('button');returned.type='button';returned.className='track-order-button';
   returned.textContent='REQUEST RETURN ↗';returned.onclick=()=>{d.close();newAfterSaleRequest(o.orderId,'RETURN')};
   const emailButton=document.createElement('button');emailButton.type='button';
   emailButton.className='track-order-button';emailButton.textContent='CONFIRMATION EMAIL STATUS ✉';
   emailButton.onclick=async()=>{
    try{
     const result=await request('/api/customer/orders/'+encodeURIComponent(o.orderId)+'/confirmation');
     let message;
     if(result.status==='SENT')message='Order #'+o.orderId+': confirmation accepted by the email provider. Check your inbox or spam folder.';
     else if(!result.sendingEnabled)message='Order #'+o.orderId+': email is queued, but the store has not enabled SMTP sending yet.';
     else if(result.status==='SENDING')message='Order #'+o.orderId+': confirmation email is being sent.';
     else if(result.status==='PENDING')message='Order #'+o.orderId+': confirmation email queued or retrying. Check again shortly.';
     else message='This older order predates email tracking.';
     notify(message);
    }catch(e){notify('Could not check email status: '+e.message,true)}
   };
   row.append(reschedule,returned,emailButton);root.append(row);
  }
  const close=document.createElement('button');close.className='outline-button';close.textContent='CLOSE';close.onclick=()=>d.close();
  root.append(close);d.showModal();
 }catch(e){notify('Order history is temporarily unavailable: '+e.message,true)}
}
function checkoutSteps(current){
 const stages=['Address','Confirm details','Payment'];
 return '<div class="checkout-steps" aria-label="Checkout progress">'+stages.map((name,index)=>'<div class="checkout-step '+(index+1===current?'active':index+1<current?'done':'')+'"><span>'+(index+1<current?'✓':index+1)+'</span><small>'+name+'</small></div>').join('')+'</div>';
}
function checkoutTotal(){
 return Object.entries(cart).reduce((sum,[id,quantity])=>{
  const product=productById(id);return sum+(product?itemPrice(product)*Number(quantity):0);
 },0);
}
function checkoutAddressText(a){
 return [a.buildingName,a.street,a.city,a.state,a.pincode,a.country].filter(Boolean).join(', ');
}
async function confirmCheckoutDetails(address){
 return new Promise(resolve=>{
  const dialog=$('modal'),root=$('modalContent');root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='Confirm details';
  root.append(heading);
  root.insertAdjacentHTML('beforeend',checkoutSteps(2));
  const section=document.createElement('section');section.className='checkout-address-card';
  section.innerHTML='<div class="checkout-card-heading"><strong>Delivering to</strong><button type="button" class="checkout-change" id="reviewChangeAddress">Change</button></div><p>'+escape(checkoutAddressText(address))+'</p><small>☎ '+escape(address.phoneNumber||'Mobile number required')+'</small>';
  root.append(section);
  section.querySelector('#reviewChangeAddress').onclick=()=>{dialog.close();resolve('address')};
  const items=document.createElement('div');items.className='checkout-review-items';root.append(items);
  const totalRow=document.createElement('div');totalRow.className='checkout-total-row';root.append(totalRow);
  const help=document.createElement('p');help.className='checkout-delivery-note';
  help.textContent='Delivery date and any shipping charges will be confirmed by the store. No guaranteed delivery date is currently available.';
  root.append(help);
  const footer=document.createElement('div');footer.className='checkout-footer';
  const back=document.createElement('button');back.type='button';back.className='checkout-secondary';back.textContent='← Address';
  back.onclick=()=>{dialog.close();resolve('address')};
  const continueBtn=document.createElement('button');continueBtn.type='button';continueBtn.className='checkout-continue';continueBtn.textContent='Continue to payment →';
  continueBtn.onclick=()=>{dialog.close();resolve('payment')};
  footer.append(back,continueBtn);root.append(footer);
  const render=()=>{
   const entries=Object.entries(cart).filter(([id,qty])=>Number(qty)>0&&productById(id));
   items.replaceChildren();
   for(const [id,qty] of entries){
    const p=productById(id),article=document.createElement('article');article.className='checkout-product';
    const artwork=document.createElement('div');artwork.className='checkout-product-photo';
    if(p.image&&/^https:\/\//.test(p.image)){const img=document.createElement('img');img.src=p.image;img.alt=p.name;img.loading='lazy';artwork.append(img)}
    else artwork.textContent=chooseIcon(p);
    const detail=document.createElement('div');detail.className='checkout-product-info';
    const name=document.createElement('strong');name.textContent=p.name;
    const desc=document.createElement('small');desc.textContent=[p.variantLabel?('Size '+p.variantLabel):'',p.category].filter(Boolean).join(' · ');
    const price=document.createElement('b');price.textContent=money(itemPrice(p)*qty);
    const controls=document.createElement('div');controls.className='checkout-quantity';
    for(const [delta,label] of [[-1,'−'],[0,String(qty)],[1,'+']]){
     if(!delta){const count=document.createElement('span');count.textContent=label;controls.append(count);continue}
     const button=document.createElement('button');button.type='button';button.textContent=label;button.setAttribute('aria-label',delta<0?'Decrease quantity':'Increase quantity');button.disabled=cartBusy;button.onclick=async()=>{
      button.disabled=true;
      const changed=await changeQty(id,delta);
      if(changed){if(!Object.values(cart).some(n=>Number(n)>0)){dialog.close();resolve('empty');return}render()}
      else render();
     };controls.append(button);
    }
    detail.append(name,desc,price,controls);article.append(artwork,detail);items.append(article);
   }
   const display=promoQuote&&promoQuote.applied
     ? '<span>After '+escape(promoQuote.code)+' offer (saved '+money(promoQuote.discount)+')</span><strong>'+money(promoQuote.total)+'</strong>'
     : '<span>Order subtotal</span><strong>'+money(checkoutTotal())+'</strong>';
   totalRow.innerHTML=display;
   continueBtn.disabled=!entries.length||cartBusy;
  };
  render();
  dialog.oncancel=e=>{e.preventDefault();dialog.close();resolve(null)};
  dialog.showModal();
 });
}
async function choosePaymentMethod(total){
 let stripeReady=false;
 try{const config=await request('/api/checkout/stripe/config');stripeReady=config.enabled===true}catch{}
 return new Promise(resolve=>{
  const dialog=$('modal'),root=$('modalContent');root.replaceChildren();
  const h=document.createElement('h2');h.textContent='Payment method';root.append(h);
  root.insertAdjacentHTML('beforeend',checkoutSteps(3));
  const intro=document.createElement('p');intro.textContent='Choose how you want to pay. Your payment and order are confirmed by the backend.';root.append(intro);
  const options=document.createElement('div');options.className='checkout-methods';root.append(options);
  let selected='COD';
  for(const [method,title,description] of [
   ['COD','Cash on Delivery','Pay when your order is delivered.'],
   ['STRIPE','Pay online with Stripe','Secure hosted card checkout; payment must be verified before order confirmation.']
  ]){
   const label=document.createElement('label');label.className='checkout-method';
   const radio=document.createElement('input');radio.type='radio';radio.name='checkoutPayment';radio.value=method;radio.checked=method==='COD';
   radio.disabled=method==='STRIPE'&&!stripeReady;
   radio.onchange=()=>{selected=method};
   const content=document.createElement('span');const strong=document.createElement('strong');strong.textContent=title;
   const sub=document.createElement('small');sub.textContent=radio.disabled?'Online payment is unavailable until Stripe is configured.':description;
   content.append(strong,sub);label.append(radio,content);options.append(label);
  }
  const summary=document.createElement('div');summary.className='checkout-total-row';summary.innerHTML='<span>'+(promoQuote?.applied?'Total after '+escape(promoQuote.code)+' (save '+money(promoQuote.discount)+')':'Total (verified at order placement)')+'</span><strong>'+money(total)+'</strong>';root.append(summary);
  const notice=document.createElement('p');notice.className='checkout-delivery-note';notice.textContent='No payment is collected for Cash on Delivery. Online payments use Stripe hosted checkout; never enter card details in this page.';root.append(notice);
  const footer=document.createElement('div');footer.className='checkout-footer';
  const back=document.createElement('button');back.type='button';back.className='checkout-secondary';back.textContent='← Review order';
  const proceed=document.createElement('button');proceed.type='button';proceed.className='checkout-continue';proceed.textContent='Place order →';
  const done=value=>{if(dialog.open)dialog.close();resolve(value)};
  back.onclick=()=>done('BACK');
  proceed.onclick=()=>done(selected);
  dialog.oncancel=e=>{e.preventDefault();done(null)};
  footer.append(back,proceed);root.append(footer);dialog.showModal();
 });
}
async function resumeStripeCheckout(){
 const url=new URL(location.href),state=url.searchParams.get('checkout');
 if(!state)return;
 url.searchParams.delete('checkout');
 const sessionId=url.searchParams.get('session_id');
 url.searchParams.delete('session_id');
 history.replaceState(null,'',url.toString());
 if(state==='stripe-cancel'){
  notify('Stripe checkout was cancelled. No order was placed; your cart is saved.');return;
 }
 if(state==='stripe-success'){
  if(!sessionId){notify('Payment returned but Stripe session reference is missing. Check your orders.',true);return}
  try{
   const data=await request('/api/checkout/stripe/status?sessionId='+encodeURIComponent(sessionId));
   if(data.status==='COMPLETED'){
    cart={};promoQuote=null;promoRequestSeq++;saveGuest();updateCart();
    try{
     const receipt=await request('/api/customer/orders/'+encodeURIComponent(data.orderId)+'/confirmation');
     notify('Stripe payment verified! Order #'+data.orderId+' confirmed. '+(receipt.sendingEnabled?'Confirmation email queued.':'Email sending awaits SMTP setup.'));
    }catch(e){notify('Stripe payment verified and order #'+data.orderId+' placed.')}

    await loadCatalog();return;
   }
   notify('Payment submitted. Order confirmation is awaiting the verified Stripe webhook. Check ORDERS shortly.');
  }catch(e){notify('Payment status needs verification: '+e.message,true)}
 }
}
async function selectDeliveryAddress(addresses){
 if(!Array.isArray(addresses)||!addresses.length)return null;
 return new Promise(resolve=>{
  const d=$('modal'),root=$('modalContent');root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='WHERE SHOULD IT GO?';root.append(heading);
  const intro=document.createElement('p');intro.textContent='Choose a saved delivery address or add another one. Your selected address is verified by Spring Boot.';root.append(intro);
  const list=document.createElement('div');list.className='address-choices';root.append(list);
  let finished=false;
  const done=value=>{if(finished)return;finished=true;d.close();resolve(value)};
  for(const a of addresses){
   const b=document.createElement('button');b.type='button';b.className='address-option';
   b.textContent=[a.buildingName,a.street,a.city,a.state,a.pincode,a.phoneNumber?('☎ '+a.phoneNumber):'MOBILE NUMBER REQUIRED'].filter(Boolean).join(' · ');
   b.disabled=!/^[6-9][0-9]{9}$/.test(a.phoneNumber||'');b.onclick=()=>done(a);list.append(b);
  }
  const add=document.createElement('button');add.type='button';add.className='primary-choice';
  add.textContent='+ ADD A NEW ADDRESS';add.onclick=()=>done(null);root.append(add);
  const cancel=document.createElement('button');cancel.type='button';cancel.className='outline-button';
  cancel.textContent='CANCEL CHECKOUT';cancel.onclick=()=>done(false);root.append(cancel);
  d.oncancel=e=>{e.preventDefault();done(false)};d.showModal();
 });
}

async function checkout(){
 let checkoutStage='checking account';
 if(cartBusy)return;
 if(!Object.values(cart).some(n=>n>0)){notify('Add a product first.',true);return}
 const session=await verifySession();
 if(!session){
  user=null;syncAccountButtons();closeCart();
  if(!await login())return;
 }
 try{
  checkoutStage='saving shopping bag';await persistCart();
  while(true){
   checkoutStage='loading delivery addresses';
   const addresses=await request('/api/users/addresses');
   let address=await selectDeliveryAddress(addresses);
   if(address===false)return;
   if(!address){
    const fields=[
     {name:'buildingName',label:'BUILDING / HOUSE',placeholder:'House 123',minLength:5},
     {name:'street',label:'STREET',placeholder:'Main Road',minLength:5},
     {name:'city',label:'CITY',placeholder:'Bengaluru'},
     {name:'state',label:'STATE',placeholder:'Karnataka'},
     {name:'country',label:'COUNTRY',placeholder:'India'},
     {name:'pincode',label:'POSTAL CODE',placeholder:'560001',minLength:6},
     {name:'phoneNumber',label:'MOBILE NUMBER (10 DIGITS)',type:'tel',placeholder:'9876543210',minLength:10}
    ];
    const data=await showDialog('DELIVERY ADDRESS',fields,'SAVE ADDRESS');
    if(!data)return;
    if(!/^[1-9][0-9]{5}$/.test(data.pincode))throw new Error('Enter a valid six-digit Indian PIN code');
    if(!/^[6-9][0-9]{9}$/.test(data.phoneNumber))throw new Error('Enter a valid ten-digit Indian mobile number');
    checkoutStage='saving delivery address';
    address=await request('/api/addresses',{method:'POST',body:JSON.stringify(data)});
   }
   if(!/^[6-9][0-9]{9}$/.test(address.phoneNumber||'')){
    notify('The delivery address needs a valid mobile number.',true);continue;
   }
   let backToAddress=false;
   while(!backToAddress){
    checkoutStage='reviewing order details';
    const review=await confirmCheckoutDetails(address);
    if(review===null||review==='empty')return;
    if(review==='address'){backToAddress=true;break}
    checkoutStage='selecting payment method';
    checkoutStage='checking offers and final totals';
    await syncPromoQuote();
    if(promoQuote?.code&&!promoQuote.applied)throw new Error(promoQuote.message||'Selected coupon no longer qualifies');
    const method=await choosePaymentMethod(promoQuote?.total??checkoutTotal());
    if(method===null)return;
    if(method==='BACK')continue;
    // The backend remains authoritative for address ownership, pricing, stock, and payment status.
    if(method==='STRIPE'){
     checkoutStage='starting Stripe hosted checkout';
     const payment=await request('/api/checkout/stripe/session',{method:'POST',
      body:JSON.stringify({addressId:address.addressId})});
     if(!payment.url||!/^https:\/\/checkout\.stripe\.com\//.test(payment.url))
      throw new Error('Stripe did not provide a valid hosted payment URL');
     window.location.assign(payment.url);return;
    }
    checkoutStage='placing cash-on-delivery order';
    const order=await request('/api/order/users/payments/COD',{method:'POST',body:JSON.stringify({
     addressId:address.addressId,pgName:'COD',pgPaymentId:null,pgStatus:'PENDING',pgResponseMessage:'Cash on delivery'
    })});
    cart={};promoQuote=null;promoRequestSeq++;saveGuest();updateCart();closeCart();
    loadOffers().catch(console.warn);
    try{
     const receipt=await request('/api/customer/orders/'+encodeURIComponent(order.orderId)+'/confirmation');
     notify('Order #'+order.orderId+' placed! Payment due on delivery. '+(receipt.sendingEnabled?'Confirmation email queued.':'Email delivery awaits store SMTP setup.'));
    }catch(e){notify('Order #'+order.orderId+' placed! Payment due on delivery.')}

    try{await loadCatalog()}catch(e){console.warn('Catalog refresh after order:',e)}
    await showOrders();return;
   }
  }
 }catch(e){notify('Checkout failed while '+checkoutStage+': '+e.message,true);console.error('Checkout stage:',checkoutStage,'Error:',e)}
}

async function saveToWishlist(productId){
 if(!user && !await login('LOGIN'))return;
 try{
  await request('/api/customer/wishlist/'+encodeURIComponent(productId),{method:'PUT'});
  notify('Product saved to your account wishlist.');
 }catch(e){notify('Could not save product: '+e.message,true)}
}
async function showWishlist(){
 if(!user && !await login('LOGIN'))return;
 try{
  const list=await request('/api/customer/wishlist');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const title=document.createElement('h2');title.textContent='YOUR SAVED OBJECTS';root.append(title);
  if(!list.length){const p=document.createElement('p');p.textContent='No saved products yet.';root.append(p)}
  for(const item of list){
   const row=document.createElement('div');row.className='review-card';
   const name=document.createElement('strong');name.textContent=item.name+' · '+money(item.price);
   const actions=document.createElement('div');actions.className='actions';
   const view=document.createElement('button');view.type='button';view.textContent='VIEW ↗';
   view.onclick=()=>{d.close();showQuickView(item.productId)};
   const remove=document.createElement('button');remove.type='button';remove.textContent='REMOVE';
   remove.onclick=async()=>{try{
    await request('/api/customer/wishlist/'+encodeURIComponent(item.productId),{method:'DELETE'});
    d.close();showWishlist();
   }catch(e){notify('Remove failed: '+e.message,true)}};
   actions.append(view,remove);row.append(name,actions);root.append(row);
  }
  const close=document.createElement('button');close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Wishlist unavailable: '+e.message,true)}
}
async function showReviews(productId){
 try{
  const reviews=await request('/api/store/products/'+encodeURIComponent(productId)+'/reviews');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='CUSTOMER FEEDBACK';root.append(heading);
  if(!reviews.length){const p=document.createElement('p');p.textContent='No purchased-product feedback yet.';root.append(p)}
  for(const item of reviews){
   const section=document.createElement('section');section.className='review-card';
   const author=document.createElement('strong');author.textContent='★ '+item.rating+'/5 · '+item.author;
   const note=document.createElement('p');note.textContent=item.comment;section.append(author,note);root.append(section);
  }
  const actions=document.createElement('div');actions.className='actions';
  const write=document.createElement('button');write.type='button';write.textContent='WRITE A REVIEW ↗';
  write.onclick=()=>{d.close();writeReview(productId)};
  const back=document.createElement('button');back.type='button';back.textContent='BACK TO PRODUCT';
  back.onclick=()=>{d.close();showQuickView(productId)};
  actions.append(write,back);root.append(actions);d.showModal();
 }catch(e){notify('Reviews unavailable: '+e.message,true)}
}
async function writeReview(productId){
 if(!user && !await login('LOGIN'))return;
 const data=await showDialog('REVIEW YOUR PURCHASE',[
  {name:'rating',label:'RATING (1 TO 5)',type:'number',placeholder:'5'},
  {name:'comment',label:'YOUR FEEDBACK (MAX 1000 CHARACTERS)',placeholder:'Describe your experience'}
 ],'PUBLISH REVIEW');
 if(!data)return;
 const rating=Number(data.rating);
 if(!Number.isInteger(rating)||rating<1||rating>5||!data.comment.trim()||data.comment.length>1000){
  notify('Enter a 1–5 rating and feedback up to 1000 characters.',true);return;
 }
 try{
  await request('/api/customer/products/'+encodeURIComponent(productId)+'/reviews',
   {method:'POST',body:JSON.stringify({rating,comment:data.comment})});
  notify('Your purchase review was saved.');showReviews(productId);
 }catch(e){notify('Review not saved: '+e.message,true)}
}
async function showRecommendations(productId){
 try{
  const items=await request('/api/store/recommendations?productId='+encodeURIComponent(productId));
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='MORE LIKE THIS';root.append(heading);
  if(!items.length){const p=document.createElement('p');p.textContent='No similar in-stock products available.';root.append(p)}
  for(const item of items){
   const b=document.createElement('button');b.type='button';b.className='address-option';
   b.textContent=item.name+' · '+(item.brand||'NOTHING SELECT')+' · '+money(item.price);
   b.onclick=()=>{d.close();showQuickView(item.productId)};root.append(b);
  }
  const close=document.createElement('button');close.type='button';close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Recommendations unavailable: '+e.message,true)}
}
async function newAfterSaleRequest(orderId,type){
 if(!user && !await login('LOGIN'))return;
 const fields=[{name:'reason',label:'REASON (AT LEAST 5 CHARACTERS)',placeholder:'Tell the store why you are requesting this'}];
 if(type==='RESCHEDULE')fields.push({name:'requestedDate',label:'PREFERRED DELIVERY DATE',type:'date'});
 const data=await showDialog(type==='RETURN'?'REQUEST A RETURN':'REQUEST A NEW DELIVERY DATE',fields,'SUBMIT REQUEST');
 if(!data)return;
 try{
  const body={type,reason:data.reason};
  if(type==='RESCHEDULE')body.requestedDate=data.requestedDate;
  const result=await request('/api/customer/orders/'+encodeURIComponent(orderId)+'/requests',
   {method:'POST',body:JSON.stringify(body)});
  notify('Request #'+result.id+' submitted for store review.');await showMyRequests();
 }catch(e){notify('Request not submitted: '+e.message,true)}
}
async function showMyRequests(){
 if(!user && !await login('LOGIN'))return;
 try{
  const items=await request('/api/customer/order-requests');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='AFTER-SALES REQUESTS';root.append(heading);
  if(!items.length){const empty=document.createElement('p');empty.textContent='No return or rescheduling requests.';root.append(empty)}
  for(const item of items){
   const block=document.createElement('section');block.className='review-card';
   const title=document.createElement('strong');
   title.textContent='ORDER #'+item.orderId+' · '+item.type+' · '+item.status;
   const note=document.createElement('p');
   note.textContent=item.reason+(item.requestedDate?' · Preferred: '+item.requestedDate:'')+
    (item.reviewNote?' · Store note: '+item.reviewNote:'');
   block.append(title,note);root.append(block);
  }
  const back=document.createElement('button');back.className='outline-button';back.textContent='BACK TO ORDERS';
  back.onclick=()=>{d.close();showOrders()};root.append(back);d.showModal();
 }catch(e){notify('Requests unavailable: '+e.message,true)}
}
async function reviewAfterSaleRequests(){
 try{
  const items=await request('/api/admin/order-requests');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='RETURN / DATE APPROVAL QUEUE';root.append(heading);
  const disclaimer=document.createElement('p');
  disclaimer.textContent='Approval does not issue refunds, restock products or book a courier. Finish those operations with verified fulfillment.';
  root.append(disclaimer);
  if(!items.length){const p=document.createElement('p');p.textContent='No pending requests.';root.append(p)}
  for(const item of items){
   const block=document.createElement('section');block.className='review-card';
   const h=document.createElement('strong');h.textContent='ORDER #'+item.orderId+' · '+item.type;
   const desc=document.createElement('p');desc.textContent=item.reason+
    (item.requestedDate?' · Requested date: '+item.requestedDate:'');
   const actions=document.createElement('div');actions.className='actions';
   for(const decision of ['APPROVED','REJECTED']){
    const b=document.createElement('button');b.type='button';b.textContent=decision;
    b.onclick=async()=>{try{
      await request('/api/admin/order-requests/'+encodeURIComponent(item.id)+'/review',
       {method:'PUT',body:JSON.stringify({decision,note:'Reviewed by store administrator'})});
      notify('Request #'+item.id+' marked '+decision.toLowerCase());d.close();reviewAfterSaleRequests();
     }catch(e){notify('Could not review request: '+e.message,true)}};
    actions.append(b);
   }
   block.append(h,desc,actions);root.append(block);
  }
  const close=document.createElement('button');close.type='button';close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Approval queue unavailable: '+e.message,true)}
}


async function changeInventory(productId,seller,type){
 const fields=type==='stock'?
  [{name:'quantity',label:'NEW STOCK QUANTITY (0 TO 1,000,000)',type:'number',placeholder:'50'}]:
  [{name:'percent',label:'PRODUCT DISCOUNT PERCENT (0 TO 80)',type:'number',placeholder:'10'}];
 const value=await showDialog(type==='stock'?'UPDATE INVENTORY':'CHANGE PRODUCT DISCOUNT',fields,'SAVE CHANGES');
 if(!value)return;
 const n=Number(type==='stock'?value.quantity:value.percent);
 if(!Number.isFinite(n)||n<0||n>(type==='stock'?1000000:80)||(type==='stock'&&!Number.isInteger(n))){
  notify('Enter a valid quantity or discount within the displayed limits.',true);return;
 }
 try{
  const section=seller?'seller':'admin';
  await request('/api/'+section+'/products/'+encodeURIComponent(productId)+'/'+type,{
   method:'PUT',body:JSON.stringify(type==='stock'?{quantity:n}:{percent:n})
  });
  notify('Inventory changes saved in PostgreSQL.');
  await loadCatalog();await showInventoryPanel(seller);
 }catch(e){notify('Inventory update failed: '+e.message,true)}
}
async function createInventoryListing(seller){
 try{
  const response=await request('/api/public/categories?pageNumber=0&pageSize=100');
  const categories=Array.isArray(response.content)?response.content:[];
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='CHOOSE A PRODUCT CATEGORY';root.append(heading);
  for(const category of categories){
   const b=document.createElement('button');b.type='button';b.className='address-option';
   b.textContent=category.categoryName;
   b.onclick=async()=>{
    d.close();
    const fields=[
     {name:'productName',label:'PRODUCT NAME (MINIMUM 3 CHARACTERS)',placeholder:'Portable Wireless Headphones'},
     {name:'description',label:'PRODUCT DESCRIPTION (10+ CHARACTERS)',placeholder:'Comfortable Bluetooth headphones with USB-C charging'},
     {name:'brand',label:'BRAND',placeholder:'NOTHING SELECT'},
     {name:'city',label:'CITY',placeholder:'Bengaluru'},
     {name:'price',label:'LIST PRICE IN INR',type:'number',placeholder:'1999'},
     {name:'quantity',label:'INITIAL STOCK',type:'number',placeholder:'20'},
     {name:'image',label:'HTTPS PRODUCT IMAGE URL (OPTIONAL)',required:false,placeholder:'https://...'}
    ];
    const input=await showDialog('ADD NEW LISTING',fields,'SAVE PRODUCT');
    if(!input)return;
    const price=Number(input.price),quantity=Number(input.quantity);
    if(price<=0||!Number.isFinite(price)||!Number.isInteger(quantity)||quantity<0||
       input.productName.length<3||input.description.length<10||quantity>1000000){
     notify('Enter a valid name, description, positive price and nonnegative stock.',true);return;
    }
    if(input.image&&!/^https:\/\//.test(input.image)){notify('Image URLs must use HTTPS.',true);return}
    const payload={...input,price,quantity,specialPrice:price,discount:0};
    try{
     const group=seller?'seller':'admin';
     await request('/api/'+group+'/categories/'+encodeURIComponent(category.categoryId)+'/product',
      {method:'POST',body:JSON.stringify(payload)});
     notify('New listing saved to PostgreSQL.');
     await loadCatalog();await showInventoryPanel(seller);
    }catch(e){notify('Product creation failed: '+e.message,true)}
   };
   root.append(b);
  }
  const close=document.createElement('button');close.type='button';close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Categories unavailable: '+e.message,true)}
}
async function showInventoryPanel(seller){
 try{
  const group=seller?'seller':'admin';
  const data=await request('/api/'+group+'/products?pageNumber=0&pageSize=50');
  const products=Array.isArray(data.products)?data.products:[];
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const heading=document.createElement('h2');heading.textContent=seller?'SELLER INVENTORY':'MARKETPLACE INVENTORY';
  root.append(heading);
  const info=document.createElement('p');info.textContent='Manage stock and discounts directly in PostgreSQL. Listed prices and cart totals are recalculated by Spring Boot.';
  root.append(info);
  const create=document.createElement('button');create.type='button';create.className='primary-choice';
  create.textContent='+ ADD PRODUCT TO CATALOG';create.onclick=()=>{d.close();createInventoryListing(seller)};
  root.append(create);
  if(!products.length){const empty=document.createElement('p');empty.textContent='No products found for this account.';root.append(empty)}
  for(const product of products){
   const id=product.productId??product.ProductId;
   if(!id)continue;
   const row=document.createElement('section');row.className='review-card';
   const name=document.createElement('strong');
   name.textContent=(product.productName??product.ProductName??'Product')+' · SKU #'+id;
   const desc=document.createElement('p');
   desc.textContent='Stock: '+(product.quantity??0)+' · Price: '+money(product.specialPrice??product.price??0)+
    ' · Discount: '+(product.discount??0)+'%';
   const actions=document.createElement('div');actions.className='actions';
   for(const [type,label] of [['stock','SET STOCK'],['discount','SET DISCOUNT']]){
    const b=document.createElement('button');b.type='button';b.textContent=label;
    b.onclick=()=>{d.close();changeInventory(id,seller,type)};
    actions.append(b);
   }
   row.append(name,desc,actions);root.append(row);
  }
  const close=document.createElement('button');close.type='button';close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Inventory unavailable: '+e.message,true)}
}

// Authenticated checkout totals always come from PostgreSQL, not browser arithmetic.
function renderPromoQuote(estimated){
 const q=promoQuote;
 const hasDiscount=!!(user&&q?.applied&&Number(q.discount)>0);
 $('couponSavingsLine').hidden=!hasDiscount;
 $('couponSavings').textContent=money(q?.discount||0);
 $('cartPayable').textContent=money(user&&q?.subtotal>=0?q.total:estimated);
 $('couponStatus').textContent=!user?'Sign in to apply coupons and see eligible offers.':
   q?.code?(q.applied?(q.automatic?'AUTOMATIC OFFER: ':'COUPON APPLIED: ')+q.code+' · '+q.message:(q.message||'This code cannot be used')):
   (q?.message||'Add a coupon or reach a spend threshold to save.');
 $('removeCouponBtn').hidden=!(user&&q?.code&&!q.automatic);
 $('couponInput').disabled=!user;
 $('applyCouponBtn').disabled=!user;
}
async function syncPromoQuote(){
 if(!user){promoQuote=null;updateCart();return null}
 const requestId=++promoRequestSeq;
 const next=await request('/api/customer/cart/quote');
 if(requestId!==promoRequestSeq)return promoQuote;
 promoQuote=next;updateCart();return next;
}
async function applyCouponCode(code){
 const normalized=String(code||'').trim().toUpperCase();
 if(!normalized){notify('Enter your coupon code.',true);return}
 if(!user){notify('Please sign in before using a coupon.',true);return}
 try{
  await persistCart();
  const q=await request('/api/customer/cart/coupon',{method:'PUT',body:JSON.stringify({code:normalized})});
  promoQuote=q;updateCart();$('couponInput').value=normalized;
  notify('Coupon '+normalized+' applied. You saved '+money(q.discount)+'.');
 }catch(e){notify('Coupon unavailable: '+e.message,true);await syncPromoQuote().catch(console.warn)}
}
async function removeCouponCode(){
 if(!user)return;
 try{
  promoQuote=await request('/api/customer/cart/coupon',{method:'DELETE'});
  $('couponInput').value='';updateCart();notify('Coupon removed. Eligible automatic offers may still apply.');
 }catch(e){notify('Could not remove coupon: '+e.message,true)}
}
function offerText(o){
 const range='ON CARTS FROM '+money(o.minSubtotal)+(o.maxSubtotal?' TO '+money(o.maxSubtotal):'');
 return o.kind==='PERCENT'?Number(o.value)+'% OFF'+(o.maxDiscount?' · UP TO '+money(o.maxDiscount):'')+' · '+range:
   money(o.value)+' OFF · '+range;
}
async function loadOffers(){
 const response=await request('/api/store/offers');
 offerList=Array.isArray(response)?response:[];
 const root=$('offersGrid');root.replaceChildren();
 if(!offerList.length){root.textContent='More offers are coming soon. Check back for limited-time promotions.';return}
 for(const offer of offerList){
  const item=document.createElement('article');item.className='offer-card';
  const tag=document.createElement('small');tag.textContent=offer.autoApply?'AUTO-APPLIED DEAL':'COUPON CODE';
  const title=document.createElement('h3');title.textContent=offer.title;
  const desc=document.createElement('p');desc.textContent=offerText(offer);
  const button=document.createElement('button');button.type='button';
  button.textContent=offer.autoApply?'SHOP TO UNLOCK ↗':'USE '+offer.code+' ↗';
  button.onclick=()=>{if(offer.autoApply){document.querySelector('#catalog').scrollIntoView({behavior:'smooth'});return}
   $('couponInput').value=offer.code;openCart();notify('Code '+offer.code+' is ready. Apply it after signing in.')};
  item.append(tag,title,desc,button);root.append(item);
 }
}
async function showOfferAdmin(){
 try{
  const offers=await request('/api/admin/offers');
  const d=$('modal'),root=$('modalContent');if(d.open)d.close();root.replaceChildren();
  const h=document.createElement('h2');h.textContent='CAMPAIGNS & COUPONS';root.append(h);
  const intro=document.createElement('p');intro.textContent='Create real PostgreSQL-backed offers with a cart spend range, validity and redemption limits. Discounts are rechecked at checkout.';root.append(intro);
  const create=document.createElement('button');create.className='primary-choice';create.textContent='+ CREATE OFFER';
  create.onclick=async()=>{
   d.close();
   const data=await showDialog('NEW PROMOTION',[
    {name:'code',label:'PROMO CODE (3–32 LETTERS/NUMBERS)',placeholder:'FESTIVE250'},
    {name:'title',label:'CAMPAIGN TITLE',placeholder:'Festival shopping bonus'},
    {name:'kind',label:'TYPE: FLAT OR PERCENT',placeholder:'FLAT'},
    {name:'value',label:'DISCOUNT ₹ OR PERCENT',type:'number',placeholder:'250'},
    {name:'minSubtotal',label:'MINIMUM CART ₹',type:'number',placeholder:'1999'},
    {name:'maxSubtotal',label:'MAXIMUM CART ₹ (OPTIONAL)',type:'number',required:false},
    {name:'maxDiscount',label:'MAXIMUM SAVING ₹ (OPTIONAL)',type:'number',required:false},
    {name:'days',label:'VALID FOR HOW MANY DAYS',type:'number',placeholder:'30'},
    {name:'globalLimit',label:'TOTAL REDEMPTION LIMIT',type:'number',placeholder:'100'},
    {name:'perCustomerLimit',label:'REDEMPTIONS PER CUSTOMER',type:'number',placeholder:'1'},
    {name:'autoApply',label:'AUTO APPLY? YES / NO',placeholder:'NO'}
   ],'CREATE COUPON');
   if(!data){showOfferAdmin();return}
   try{
    const payload={
     code:data.code,title:data.title,kind:String(data.kind).toUpperCase(),
     value:Number(data.value),minSubtotal:Number(data.minSubtotal),
     maxSubtotal:data.maxSubtotal?Number(data.maxSubtotal):null,
     maxDiscount:data.maxDiscount?Number(data.maxDiscount):null,
     startsAt:new Date(Date.now()-60000).toISOString(),
     endsAt:new Date(Date.now()+Number(data.days)*86400000).toISOString(),
     globalLimit:Number(data.globalLimit),perCustomerLimit:Number(data.perCustomerLimit),
     autoApply:String(data.autoApply).toLowerCase()==='yes'
    };
    if(!Number.isFinite(payload.value)||!Number.isFinite(payload.minSubtotal)||!Number.isFinite(Number(data.days))||Number(data.days)<1)throw Error('Please enter valid offer amounts and dates');
    await request('/api/admin/offers',{method:'POST',body:JSON.stringify(payload)});
    notify('Offer '+payload.code+' published.');await loadOffers();showOfferAdmin();
   }catch(e){notify('Could not create offer: '+e.message,true);showOfferAdmin()}
  };
  root.append(create);
  for(const o of offers){
   const row=document.createElement('article');row.className='review-card';
   const name=document.createElement('strong');name.textContent=o.code+' · '+o.title;
   const details=document.createElement('p');details.textContent=offerText(o)+' · Redeemed '+o.usedCount+'/'+o.globalLimit+' · '+(o.active?'ACTIVE':'PAUSED');
   const toggle=document.createElement('button');toggle.type='button';toggle.className='outline-button';
   toggle.textContent=o.active?'PAUSE OFFER':'ACTIVATE OFFER';
   toggle.onclick=async()=>{try{
    await request('/api/admin/offers/'+o.id+'/active',{method:'PUT',body:JSON.stringify({active:!o.active})});
    await loadOffers();d.close();showOfferAdmin();
   }catch(e){notify(e.message,true)}};
   row.append(name,details,toggle);root.append(row);
  }
  const close=document.createElement('button');close.type='button';close.className='outline-button';close.textContent='CLOSE';
  close.onclick=()=>d.close();root.append(close);d.showModal();
 }catch(e){notify('Admin offers unavailable: '+e.message,true)}
}

function bind(){
 $('year').textContent=new Date().getFullYear();
 $('cartBtn').onclick=openCart;$('closeCart').onclick=closeCart;$('overlay').onclick=closeCart;
 $('accountBtn').onclick=accountAction;
 $('signInBtn').onclick=()=>login('LOGIN');
 $('registerBtn').onclick=()=>login('SIGNUP');
 $('logoutBtn').onclick=logout;
 $('ordersBtn').onclick=showOrders;$('wishlistBtn').onclick=showWishlist;$('checkoutBtn').onclick=checkout;
 $('retailerBtn').onclick=retailerAction;$('retailerCta').onclick=retailerAction;$('adminRetailersBtn').onclick=reviewRetailers;
 $('adminOrdersBtn').onclick=showFulfillmentPanel;
 $('adminRequestsBtn').onclick=reviewAfterSaleRequests;
 $('adminInventoryBtn').onclick=()=>showInventoryPanel(false);
 $('adminOffersBtn').onclick=showOfferAdmin;
 $('applyCouponBtn').onclick=()=>applyCouponCode($('couponInput').value);
 $('removeCouponBtn').onclick=removeCouponCode;
 $('couponInput').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();applyCouponCode($('couponInput').value)}};
 $('sellerInventoryBtn').onclick=()=>showInventoryPanel(true);
 $('searchGo').onclick=()=>{searchText=$('search').value.trim();applyFilters();$('catalog').scrollIntoView({behavior:'smooth'})};
 $('search').addEventListener('keydown',e=>{if(e.key==='Enter'){clearTimeout(searchDebounce);$('searchGo').click()}});
 $('search').addEventListener('input',()=>{clearTimeout(searchDebounce);searchDebounce=setTimeout(()=>{searchText=$('search').value.trim();applyFilters()},350)});
 $('search').addEventListener('search',()=>{searchText=$('search').value.trim();applyFilters()});
 ['cityFilter','priceFilter','sortFilter','brandFilter'].forEach(id=>$(id).onchange=applyFilters);
 document.querySelectorAll('[data-category-nav]').forEach(el=>el.onclick=()=>setCategory(el.dataset.categoryNav));
 $('clearFilters').onclick=()=>{searchText='';$('search').value='';$('cityFilter').value='';$('priceFilter').value='';$('brandFilter').value='';setCategory('All')};
 document.addEventListener('keydown',e=>{if(e.key==='Escape')closeCart()});
}
bind();syncAccountButtons();loadOffers().catch(console.warn);loadCatalog().then(loadSession).then(resumeStripeCheckout);
})();