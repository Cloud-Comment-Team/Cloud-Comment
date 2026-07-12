import { createReadStream, existsSync, readdirSync, statSync } from 'node:fs'
import { basename, join } from 'node:path'
import { createGzip } from 'node:zlib'

const budgets = [
  { dir: 'widget-frontend/dist/widget', match: (name) => name.endsWith('.js'), limit: 20 * 1024, label: 'пакет виджета' },
  { dir: 'frontend/admin/dist/assets', match: (name) => /^index-.*\.js$/.test(name), limit: 180 * 1024, label: 'основной пакет админки' },
  { dir: 'frontend/admin/dist/assets', match: (name) => name.endsWith('.js') && !/^index-/.test(name), limit: 60 * 1024, label: 'маршрутный пакет админки' },
]

function gzipSize(path) {
  return new Promise((resolve, reject) => {
    let size = 0
    createReadStream(path).pipe(createGzip({ level: 9 })).on('data', (chunk) => { size += chunk.length }).on('end', () => resolve(size)).on('error', reject)
  })
}

let failed = false
for (const budget of budgets) {
  if (!existsSync(budget.dir)) throw new Error(`Не найден каталог сборки: ${budget.dir}`)
  const files = readdirSync(budget.dir).map((name) => join(budget.dir, name)).filter((path) => statSync(path).isFile() && budget.match(basename(path)))
  for (const file of files) {
    const size = await gzipSize(file)
    const ok = size <= budget.limit
    console.log(`${ok ? 'OK' : 'FAIL'} ${budget.label}: ${basename(file)} ${(size / 1024).toFixed(1)} КБ / ${budget.limit / 1024} КБ gzip`)
    failed ||= !ok
  }
}
if (failed) process.exit(1)
