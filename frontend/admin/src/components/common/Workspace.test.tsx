import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Dialog, Drawer, PageHeader } from './Workspace'

describe('workspace components', () => {
  it('создаёт один понятный заголовок страницы', () => {
    render(<PageHeader eyebrow="Сайты" title="Настройки" description="Оформление виджета" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Настройки' })).toBeInTheDocument()
  })

  it('закрывает боковую панель доступной кнопкой', () => {
    const onClose = vi.fn()
    render(<Drawer title="Подробности" open onClose={onClose}>Содержимое</Drawer>)
    fireEvent.click(screen.getByRole('button', { name: 'Закрыть панель' }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('не монтирует закрытый диалог', () => {
    const { rerender } = render(<Dialog title="Удаление" open={false} onClose={() => undefined}>Текст</Dialog>)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    rerender(<Dialog title="Удаление" open onClose={() => undefined}>Текст</Dialog>)
    expect(screen.getByRole('dialog', { name: 'Удаление' })).toBeInTheDocument()
  })

  it('удерживает фокус в диалоге, закрывается по Escape и возвращает фокус', () => {
    const onClose = vi.fn()
    const trigger = document.createElement('button')
    trigger.textContent = 'Открыть'
    document.body.append(trigger)
    trigger.focus()

    const { unmount } = render(
      <Dialog
        title="Несохранённые изменения"
        open
        onClose={onClose}
        actions={<><button>Остаться</button><button>Уйти</button></>}
      >
        Текст
      </Dialog>,
    )

    const stayButton = screen.getByRole('button', { name: 'Остаться' })
    const leaveButton = screen.getByRole('button', { name: 'Уйти' })
    expect(stayButton).toHaveFocus()
    leaveButton.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(stayButton).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()

    unmount()
    expect(trigger).toHaveFocus()
    trigger.remove()
  })
})
