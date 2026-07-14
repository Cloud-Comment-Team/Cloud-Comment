import { defineConfig, loadEnv, type Plugin } from "vite";
import { transform as transformCss } from "lightningcss";
import { readFileSync } from "node:fs";

const preservedClassNames = new Set([
  "cloud-comment",
  "cloud-comment__button",
  "cloud-comment__comment",
  "cloud-comment__comment-content",
  "cloud-comment__comment-header",
  "cloud-comment__form",
  "cloud-comment__message--error",
  "cloud-comment__message--muted",
  "cloud-comment__message--notice",
  "cloud-comment__pinned",
  "cloud-comment__reaction--active",
  "cloud-comment__reaction-count",
  "cloud-comment__replies",
  "cloud-comment__reply-button",
  "cloud-comment__reply-context",
  "cloud-comment__status",
  "cloud-comment__title"
]);
const widgetStyleSource = readFileSync(new URL("./src/widget/styles.ts", import.meta.url), "utf8");
const compactClassNames = [...new Set(
  [...widgetStyleSource.matchAll(/\.([a-z][a-z0-9_-]*)/giu)].map((match) => match[1])
)]
  .filter((className) => !preservedClassNames.has(className))
  .sort((left, right) => right.length - left.length)
  .map((className, index) => [className, `c${index.toString(36)}`] as const);

function replaceRenderClassNames(source: string): string {
  return compactClassNames.reduce(
    (result, [className, compactName]) => result.replace(
      new RegExp(`(?<![a-z0-9_-])${className}(?![a-z0-9_-])`, "giu"),
      compactName
    ),
    source
  );
}

function replaceCssClassNames(source: string): string {
  return compactClassNames.reduce(
    (result, [className, compactName]) => result.replace(
      new RegExp(`\\.${className}(?![a-z0-9_-])`, "giu"),
      `.${compactName}`
    ),
    source
  );
}

function optimizeWidgetRuntime(): Plugin {
  return {
    name: "optimize-widget-runtime",
    transform(source: string, id: string) {
      const normalizedId = id.replace(/\\/gu, "/");
      if (normalizedId.endsWith("/src/widget/render.ts")) {
        return { code: replaceRenderClassNames(source), map: null };
      }
      if (!normalizedId.endsWith("/src/widget/styles.ts")) {
        return null;
      }
      const css = source.match(/export const widgetStyles = `([\s\S]*?)`;/u)?.[1];
      if (css === undefined) {
        return null;
      }
      const minified = new TextDecoder().decode(transformCss({
        filename: "cloud-comment-widget.css",
        code: new TextEncoder().encode(replaceCssClassNames(css)),
        minify: true
      }).code);
      this.emitFile({
        type: "asset",
        fileName: "cloud-comment-widget.css",
        source: minified
      });
      return {
        code: [
          'export const widgetStyles = "";',
          'export const widgetStylesUrl = "./cloud-comment-widget.css";'
        ].join("\n"),
        map: null
      };
    }
  };
}

function emitFramePage(apiOrigin: string): Plugin {
  return {
    name: "emit-widget-frame-page",
    generateBundle() {
      this.emitFile({
        type: "asset",
        fileName: "frame.html",
        source: `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self'; connect-src ${apiOrigin}; style-src 'self' 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; object-src 'none'">
  <meta name="cloud-comment-api-origin" content="${apiOrigin}">
  <title>CloudComment</title>
  <style>html,body{margin:0;padding:0;background:transparent}</style>
</head>
<body>
  <div id="cloud-comment-widget-frame"></div>
  <script src="./cloud-comment-widget-frame.js" defer></script>
</body>
</html>
`
      });
    }
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, "..", "");
  const apiBaseUrl = env.VITE_CLOUD_COMMENT_API_BASE_URL?.trim() || "http://localhost/api";
  const apiOrigin = new URL(apiBaseUrl).origin;
  return {
    envDir: "..",
    publicDir: false,
    plugins: [optimizeWidgetRuntime(), emitFramePage(apiOrigin)],
    build: {
      emptyOutDir: false,
      outDir: "dist/widget",
      lib: {
        entry: "src/widget/frame.ts",
        name: "CloudCommentWidgetFrame",
        formats: ["iife"],
        fileName: () => "cloud-comment-widget-frame.js"
      }
    }
  };
});
