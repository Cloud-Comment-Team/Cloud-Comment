import { defineConfig } from "vite";

export default defineConfig({
  envDir: "..",
  publicDir: "public",
  build: {
    emptyOutDir: true,
    outDir: "dist/widget",
    lib: {
      entry: "src/widget/index.ts",
      name: "CloudCommentWidget",
      formats: ["iife"],
      fileName: () => "cloud-comment-widget.js"
    }
  }
});
