const CACHE="clipforge-v1.1.0";
const ASSETS=["./","index.html","manifest.webmanifest","icon.svg","icon-192.png","icon-512.png","lib/ffmpeg.js","lib/814.ffmpeg.js","core/ffmpeg-core.js","core/ffmpeg-core.wasm"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE).map(x=>caches.delete(x)))).then(()=>self.clients.claim()));});
self.addEventListener("fetch",e=>{
  if(e.request.method!=="GET") return;
  e.respondWith(caches.match(e.request,{ignoreSearch:true}).then(r=>r||fetch(e.request)));
});
