import { defineConfig } from "vite";

// `npm run dev` proxies the API to `sciologer serve --no-open` on port 8765.
// `npm run build` writes into the Python package so `sciologer serve` can serve it.
export default defineConfig({
  server: { proxy: { "/api": "http://127.0.0.1:8765" } },
  build: { outDir: "../sciologer/static", emptyOutDir: true },
});
