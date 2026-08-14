import { defineConfig } from '../frontend/node_modules/vite/dist/node/index.js'
import vue from '../frontend/node_modules/@vitejs/plugin-vue/dist/index.mjs'
import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

export default defineConfig({
  root: resolve('.'),
  plugins: [
    vue(),
    {
      name: 'save-pms-layout',
      configureServer(server) {
        server.middlewares.use('/layout-save', (req, res) => {
          if (req.method !== 'POST') { res.statusCode = 405; res.end(); return }
          const chunks = []
          req.on('data', chunk => chunks.push(chunk))
          req.on('end', async () => {
            try {
              const data = JSON.parse(Buffer.concat(chunks).toString('utf8'))
              data.savedAt = new Date().toISOString()
              await writeFile(resolve('layout.json'), `${JSON.stringify(data, null, 2)}\n`)
              res.setHeader('Content-Type', 'application/json')
              res.end(JSON.stringify({ ok: true, savedAt: data.savedAt }))
            } catch { res.statusCode = 400; res.end(JSON.stringify({ ok: false })) }
          })
        })
      },
    },
  ],
  resolve: {
    alias: {
      vue: resolve('../frontend/node_modules/vue/dist/vue.esm-bundler.js'),
      'element-plus': resolve('../frontend/node_modules/element-plus'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 4174,
    proxy: {
      '/pms': { target: 'http://127.0.0.1:5173', changeOrigin: true, ws: true },
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
    fs: { allow: [resolve('..')] },
  },
})
