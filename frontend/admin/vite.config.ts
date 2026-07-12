import { defineConfig, type Plugin, type ViteDevServer } from 'vite'
import react from '@vitejs/plugin-react'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const widgetBundlePath = fileURLToPath(new URL('../../widget-frontend/dist/widget/cloud-comment-widget.js', import.meta.url))

function widgetPreviewBundle(): Plugin {
  return {
    name: 'cloud-comment-widget-preview-bundle',
    configureServer(server: ViteDevServer) {
      server.middlewares.use('/widget/cloud-comment-widget.js', async (_request, response, next) => {
        try {
          const bundle = await readFile(widgetBundlePath)
          response.statusCode = 200
          response.setHeader('Content-Type', 'text/javascript; charset=utf-8')
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
