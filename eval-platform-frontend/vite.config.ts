/// <reference types="vitest/config" />
import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  test: {
    // Le vrai Monaco ne tourne pas dans jsdom : les tests reçoivent une configuration vide (voir src/lib/monaco.vitest.ts)
    alias: [{ find: /^@\/lib\/monaco$/, replacement: path.resolve(__dirname, "./src/lib/monaco.vitest.ts") }],
  },
})
