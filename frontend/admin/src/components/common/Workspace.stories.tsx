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

function LayersExample() {
  const [drawer, setDrawer] = useState(false)
  const [dialog, setDialog] = useState(false)
  return <><div className="flex gap-2"><Button onClick={() => setDrawer(true)}>Открыть панель</Button><Button variant="danger" onClick={() => setDialog(true)}>Опасное действие</Button></div><Drawer title="Подробности" open={drawer} onClose={() => setDrawer(false)}>Контекст выбранного элемента</Drawer><Dialog title="Удалить элемент?" open={dialog} onClose={() => setDialog(false)} actions={<><Button onClick={() => setDialog(false)}>Отмена</Button><Button variant="danger">Удалить</Button></>}>Действие нельзя отменить.</Dialog></>
}

export const Layers: Story = {
  render: () => <LayersExample />,
}
