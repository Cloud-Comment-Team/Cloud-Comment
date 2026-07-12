import { readFileSync } from 'node:fs'

const forbidden = [/\uFFFD/u, /^\uFEFF/u, /(?:Р[А-Яа-яЁё]|С[А-Яа-яЁё]){2,}/u, /Ð.|Ñ./u]
const eventPath = process.env.GITHUB_EVENT_PATH
const targets = [
  ['шаблон PR', readFileSync('.github/pull_request_template.md', 'utf8')],
  ['шаблон задачи', readFileSync('.github/ISSUE_TEMPLATE/feature.yml', 'utf8')],
]

if (eventPath && process.env.GITHUB_EVENT_NAME === 'pull_request') {
  const event = JSON.parse(readFileSync(eventPath, 'utf8'))
  targets.push(['заголовок PR', event.pull_request?.title ?? ''], ['описание PR', event.pull_request?.body ?? ''])
}

let failed = false
for (const [name, value] of targets) {
  if (!/[А-Яа-яЁё]/u.test(value)) {
    console.error(`${name}: отсутствует кириллица`)
    failed = true
  }
  if (forbidden.some((pattern) => pattern.test(value))) {
    console.error(`${name}: обнаружены BOM, U+FFFD или mojibake`)
    failed = true
  }
}
if (failed) process.exit(1)
console.log('Русские GitHub-артефакты корректны и сохранены в UTF-8 без BOM.')
