import { defineConfig, type Plugin, type ViteDevServer } from 'vite'
import react from '@vitejs/plugin-react'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const widgetBundlePath = fileURLToPath(new URL('../../widget-frontend/dist/widget/cloud-comment-widget.js', import.meta.url))
const widgetFrameBundlePath = fileURLToPath(new URL('../../widget-frontend/dist/widget/cloud-comment-widget-frame.js', import.meta.url))
const widgetStylesPath = fileURLToPath(new URL('../../widget-frontend/dist/widget/cloud-comment-widget.css', import.meta.url))

function widgetPreviewBundle(): Plugin {
  return {
    name: 'cloud-comment-widget-preview-bundle',
    configureServer(server: ViteDevServer) {
      const bundles = new Map([
        ['/widget/cloud-comment-widget.js', widgetBundlePath],
        ['/widget/cloud-comment-widget-frame.js', widgetFrameBundlePath],
        ['/widget/cloud-comment-widget.css', widgetStylesPath],
      ])
      server.middlewares.use(async (request, response, next) => {
        const pathname = request.url?.split('?', 1)[0]
        const bundlePath = pathname ? bundles.get(pathname) : undefined
        if (!bundlePath) {
          next()
          return
        }
        try {
          const bundle = await readFile(bundlePath)
          response.statusCode = 200
          response.setHeader(
            'Content-Type',
            pathname?.endsWith('.css') ? 'text/css; charset=utf-8' : 'text/javascript; charset=utf-8',
          )
          response.end(bundle)
        } catch {
          next()
        }
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  envDir: '../..',
  plugins: [react(), widgetPreviewBundle()],
  build: {
    manifest: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
