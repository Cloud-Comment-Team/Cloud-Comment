import type { Meta, StoryObj } from '@storybook/react-vite'
import { AsyncState } from './AsyncState'

const meta = {
  title: 'Система/Асинхронное состояние',
  component: AsyncState,
  args: { children: <div className="cc-card p-6">Содержимое загружено</div> },
} satisfies Meta<typeof AsyncState>

export default meta
type Story = StoryObj<typeof meta>

export const Content: Story = {}
export const Loading: Story = { args: { loading: true } }
export const Empty: Story = { args: { empty: true, emptyMessage: 'Здесь пока ничего нет' } }
export const Error: Story = { args: { error: 'Не удалось загрузить данные' } }
