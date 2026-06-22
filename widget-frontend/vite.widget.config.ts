import { defineConfig } from "vite";

export default defineConfig({
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
