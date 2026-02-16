import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { visualizer } from "rollup-plugin-visualizer";
import path from "path";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    ...(process.env.ANALYZE === "true"
      ? [visualizer({ open: true, filename: "dist/stats.html" })]
      : []),
  ],
  base: "/",
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/v1": process.env.VITE_API_TARGET || "http://localhost:8080",
      "/v3": process.env.VITE_API_TARGET || "http://localhost:8080",
      "/scalar.html": process.env.VITE_API_TARGET || "http://localhost:8080",
    },
  },
  build: {
    outDir: "dist",
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ["react", "react-dom", "react-router-dom"],
          query: ["@tanstack/react-query", "axios"],
        },
      },
    },
  },
});
