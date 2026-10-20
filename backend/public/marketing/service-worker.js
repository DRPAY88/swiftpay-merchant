const CACHE_NAME = "drpay-marketing-v1";
const PRECACHE_URLS = [
  "/",
  "/site.css?v=4",
  "/rates.js?v=3",
  "/pwa.js?v=1",
  "/manifest.webmanifest",
  "/apple-touch-icon.png",
  "/drpay-pwa-192.png",
  "/drpay-pwa-512.png",
  "/checkout-assets/drpay_mark.png"
];
const CACHEABLE_PATHS = new Set([
  "/",
  "/site.css",
  "/rates.js",
  "/pwa.js",
  "/manifest.webmanifest",
  "/apple-touch-icon.png",
  "/drpay-pwa-192.png",
  "/drpay-pwa-512.png",
  "/checkout-assets/drpay_mark.png"
]);

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => Promise.all(
        cacheNames
          .filter((cacheName) => cacheName.startsWith("drpay-marketing-") && cacheName !== CACHE_NAME)
          .map((cacheName) => caches.delete(cacheName))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);
  const isHomePage = request.mode === "navigate" && url.origin === self.location.origin && url.pathname === "/";
  const isStaticAsset = request.method === "GET" && url.origin === self.location.origin && CACHEABLE_PATHS.has(url.pathname);

  if (!isHomePage && !isStaticAsset) return;

  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response.ok && response.type === "basic") {
          event.waitUntil(
            caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()))
          );
        }
        return response;
      })
      .catch(async () => {
        const cached = await caches.match(request, { ignoreSearch: true });
        if (cached) return cached;
        if (isHomePage) return caches.match("/");
        return Response.error();
      })
  );
});
