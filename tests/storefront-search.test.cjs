const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

// Exercise the actual storefront helpers without requiring a browser or a mock search server.
const source=fs.readFileSync(path.join(__dirname,'../nothing-coffee/app.js'),'utf8');
const start=source.indexOf('function searchTokens(value){');
const end=source.indexOf('\nconst escape=',start);
assert.ok(start>=0&&end>start,'Search helpers must exist in storefront app.js');
const context=vm.createContext({category:'All'});
vm.runInContext(source.slice(start,end),context);

test('fase wuh finds face wash, not unrelated products',()=>{
 const items=[
  {name:'Hydrating Face Wash',description:'Gentle facial cleanser',brand:'Nova',city:'Bengaluru',price:299,category:'Beauty'},
  {name:'Wireless Headphones',description:'Bluetooth audio product',brand:'Audio',city:'Pune',price:899,category:'Electronics'},
  {name:'Water Bottle',description:'Reusable bottle',brand:'Home',city:'Mysuru',price:399,category:'Home & Living'}
 ];
 const result=context.localCatalogSearch(items,'fase wuh');
 assert.equal(result.length,1);
 assert.equal(result[0].name,'Hydrating Face Wash');
 assert.equal(context.wordEditDistance('fase','face'),1);
 assert.equal(context.wordEditDistance('wuh','wash'),2);
});

test('unlisted seller cities work with case-insensitive filtering',()=>{
 const products=[
  {name:'Demo Product',city:'  Tirupati ',category:'Electronics',price:1500,brand:'Nova'},
  {name:'Second Product',city:'Bengaluru',category:'Electronics',price:1500,brand:'Nova'}
 ];
 const matches=context.filterCatalogLocally(products,'TIRUPATI','1000-2000','Nova');
 assert.equal(matches.length,1);
 assert.equal(matches[0].name,'Demo Product');
});

test('face wash only shows relevant skin cleansers, not earbuds or headphones',()=>{
 const items=[
  {name:'Hydrating Face Wash',description:'Gentle face cleanser',brand:'Nova'},
  {name:'Bluetooth Earbuds with Charging Case',description:'Portable audio case, washable materials',brand:'Audio'},
  {name:'Wireless Headphones',description:'Comfortable headset with a carrying case',brand:'Audio'},
  {name:'Face Serum',description:'Lightweight beauty serum',brand:'Beauty'},
  {name:'Body Wash',description:'Daily body cleansing product',brand:'Beauty'}
 ];
 assert.deepEqual(context.localCatalogSearch(items,'face wash').map(x=>x.name),['Hydrating Face Wash']);
 assert.deepEqual(context.localCatalogSearch(items,'fase wuh').map(x=>x.name),['Hydrating Face Wash']);
});
test('exact description matching is supported without fuzzy matching description noise',()=>{
 const items=[
  {name:'Portable Cleanser',description:'A gentle face wash for daily use',brand:'Beauty'},
  {name:'Wireless Earbuds',description:'Case with waterproof materials',brand:'Audio'}
 ];
 assert.deepEqual(context.localCatalogSearch(items,'face wash').map(x=>x.name),['Portable Cleanser']);
});
test('invalid search words do not return every product',()=>{
 const items=[{name:'Hydrating Face Wash',description:'Gentle cleanser',brand:'Nova'}];
 assert.equal(context.localCatalogSearch(items,'unrelatedxyz').length,0);
});
