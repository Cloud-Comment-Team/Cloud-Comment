import { createReadStream, existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { basename, join } from 'node:path'
import { createGzip } from 'node:zlib'

const adminDistDir = 'frontend/admin/dist'
const adminAssetsDir = join(adminDistDir, 'assets')
const analyticsGraphLimit = 120 * 1024
const analyticsEntrySuffix = 'src/components/analytics/AnalyticsTrendChart.tsx'

function gzipSize(path) {
  return new Promise((resolve, reject) => {
    let size = 0
    createReadStream(path)
      .pipe(createGzip({ level: 9 }))
      .on('data', (chunk) => { size += chunk.length })
      .on('end', () => resolve(size))
      .on('error', reject)
  })
}

function collectStaticClosure(manifest, initialKeys) {
  const result = new Set()
  const pending = [...initialKeys]
  while (pending.length > 0) {
    const key = pending.pop()
    if (!key || result.has(key)) continue
    const item = manifest[key]
    if (!item) throw new Error(`Манифест сборки ссылается на неизвестный пакет: ${key}`)
    result.add(key)
    pending.push(...(item.imports ?? []))
  }
  return result
}

function readAnalyticsGraph() {
  const manifestPath = join(adminDistDir, '.vite', 'manifest.json')
  if (!existsSync(manifestPath)) {
    throw new Error(`Не найден манифест сборки: ${manifestPath}`)
  }

  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  const analyticsKey = Object.keys(manifest).find((key) => key.replaceAll('\\', '/').endsWith(analyticsEntrySuffix))
  if (!analyticsKey) {
    throw new Error(`В манифесте не найден ленивый пакет: ${analyticsEntrySuffix}`)
  }

  const importerKeys = Object.entries(manifest)
    .filter(([, item]) => item.dynamicImports?.includes(analyticsKey))
    .map(([key]) => key)
  const initialKeys = Object.entries(manifest)
    .filter(([, item]) => item.isEntry)
    .map(([key]) => key)
  const alreadyLoaded = collectStaticClosure(manifest, [...initialKeys, ...importerKeys])
  const analyticsClosure = collectStaticClosure(manifest, [analyticsKey])
  const graphKeys = [...analyticsClosure].filter((key) => !alreadyLoaded.has(key))
  const files = new Set(
    graphKeys
      .map((key) => manifest[key]?.file)
      .filter((file) => typeof file === 'string' && file.endsWith('.js')),
  )
  if (files.size === 0) {
    throw new Error('Ленивый граф аналитики оказался пустым; бюджет нельзя проверить достоверно.')
  }
  return files
}

const analyticsGraphFiles = readAnalyticsGraph()
const budgets = [
  { dir: 'widget-frontend/dist/widget', match: (name) => name.endsWith('.js'), limit: 20 * 1024, label: 'пакет виджета' },
  { dir: adminAssetsDir, match: (name) => /^index-.*\.js$/.test(name), limit: 180 * 1024, label: 'основной пакет админки' },
  {
    dir: adminAssetsDir,
    match: (name) => name.endsWith('.js') && !/^index-/.test(name) && !analyticsGraphFiles.has(`assets/${name}`),
    limit: 60 * 1024,
    label: 'маршрутный пакет админки',
  },
]

let failed = false
for (const budget of budgets) {
  if (!existsSync(budget.dir)) throw new Error(`Не найден каталог сборки: ${budget.dir}`)
  const files = readdirSync(budget.dir)
    .map((name) => join(budget.dir, name))
    .filter((path) => statSync(path).isFile() && budget.match(basename(path)))
  for (const file of files) {
    const size = await gzipSize(file)
    const ok = size <= budget.limit
    console.log(`${ok ? 'OK' : 'FAIL'} ${budget.label}: ${basename(file)} ${(size / 1024).toFixed(1)} КБ / ${budget.limit / 1024} КБ gzip`)
    failed ||= !ok
  }
}

let analyticsGraphSize = 0
for (const relativePath of analyticsGraphFiles) {
  const path = join(adminDistDir, relativePath)
  if (!existsSync(path)) throw new Error(`Не найден пакет ленивого графа аналитики: ${path}`)
  analyticsGraphSize += await gzipSize(path)
}
const analyticsGraphOk = analyticsGraphSize <= analyticsGraphLimit
console.log(
  `${analyticsGraphOk ? 'OK' : 'FAIL'} ленивый граф аналитики: ${(analyticsGraphSize / 1024).toFixed(1)} КБ / ${analyticsGraphLimit / 1024} КБ gzip `
  + `(${[...analyticsGraphFiles].map((path) => basename(path)).join(', ')})`,
)
failed ||= !analyticsGraphOk

if (failed) process.exit(1)
