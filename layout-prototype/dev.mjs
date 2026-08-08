import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = dirname(fileURLToPath(import.meta.url))
const vite = join(root, '../frontend/node_modules/vite/bin/vite.js')
const child = spawn(process.execPath, [vite, '--config', join(root, 'vite.config.mjs')], { cwd: root, stdio: 'inherit' })
child.on('exit', code => process.exit(code ?? 0))
