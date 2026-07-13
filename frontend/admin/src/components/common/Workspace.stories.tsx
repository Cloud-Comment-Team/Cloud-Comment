import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react-vite'
import Button from './Button/Button'
import Card from './Card/Card'
import { ActionBar, DataRow, Dialog, Drawer, FilterBar, PageHeader } from './Workspace'

const meta = { title: 'Система/Рабочее пространство' } satisfies Meta
export default meta
type Story = StoryObj<typeof meta>

export const Overview: Story = {
  render: () => (
    <div className="cc-page">
      <PageHeader eyebrow="Раздел" title="Заголовок страницы" description="Коротко объясняет назначение и следующий шаг." actions={<Button variant="primary">Главное действие</Button>} />
      <ActionBar><strong style={{ color: 'var(--text-h)' }}>12 элементов</strong><Button>Экспорт</Button></ActionBar>
      <FilterBar><label>Поиск<input className="cc-field mt-1" placeholder="Название или адрес" /></label><Button type="submit">Применить</Button></FilterBar>
      <Card className="mt-4 !p-0"><DataRow compact>Компактная строка данных</DataRow><DataRow>Обычная строка данных</DataRow></Card>
    </div>
  ),
}

export const EditorialList: Story = {
  render: () => (
    <div className="cc-page">
      <PageHeader
        title="Сайты"
        description="Подключение и состояние виджетов без дублирующих панелей"
        actions={<Button variant="primary">Создать сайт</Button>}
      />
      <section className="cc-section overflow-hidden">
        <div className="cc-section-header">
          <div>
            <h2 className="cc-section-title">Подключённые сайты</h2>
            <p className="cc-section-description">Три проекта в CloudComment</p>
          </div>
        </div>
        <div className="cc-list-heading md:grid-cols-[minmax(12rem,1.4fr)_minmax(9rem,0.8fr)_8rem_10rem]">
          <span>Сайт</span><span>Модерация</span><span>Статус</span><span>Создан</span>
        </div>
        {['Редакция', 'Документация', 'Блог продукта'].map((name, index) => (
          <div key={name} className="cc-list-row md:grid-cols-[minmax(12rem,1.4fr)_minmax(9rem,0.8fr)_8rem_10rem]">
            <div><strong style={{ color: 'var(--text-h)' }}>{name}</strong><p className="text-sm" style={{ color: 'var(--text)' }}>project-{index + 1}.example</p></div>
            <span className="text-sm">{index === 0 ? 'Премодерация' : 'Постмодерация'}</span>
            <span className="text-sm">Активен</span>
            <span className="text-sm">13 июля 2026</span>
          </div>
        ))}
      </section>
    </div>
  ),
}

function LayersExample() {
  const [drawer, setDrawer] = useState(false)
  const [dialog, setDialog] = useState(false)
  return <><div className="flex gap-2"><Button onClick={() => setDrawer(true)}>Открыть панель</Button><Button variant="danger" onClick={() => setDialog(true)}>Опасное действие</Button></div><Drawer title="Подробности" open={drawer} onClose={() => setDrawer(false)}>Контекст выбранного элемента</Drawer><Dialog title="Удалить элемент?" open={dialog} onClose={() => setDialog(false)} actions={<><Button onClick={() => setDialog(false)}>Отмена</Button><Button variant="danger">Удалить</Button></>}>Действие нельзя отменить.</Dialog></>
}

export const Layers: Story = {
  render: () => <LayersExample />,
}
