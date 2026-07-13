import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Dialog, Drawer, PageHeader } from './Workspace'

function DrawerHarness() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Открыть панель</button>
      <Drawer title="Подробности" open={open} onClose={() => setOpen(false)}>
        <button type="button">Первое действие</button>
        <button type="button">Последнее действие</button>
      </Drawer>
    </>
  )
}

describe('workspace components', () => {
  it('создаёт один понятный заголовок страницы', () => {
    render(<PageHeader eyebrow="Сайты" title="Настройки" description="Оформление виджета" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Настройки' })).toBeInTheDocument()
  })

  it('удерживает фокус в панели, закрывается по Escape и возвращает фокус', () => {
    const appRoot = document.createElement('div')
    appRoot.id = 'root'
    document.body.append(appRoot)
    const { unmount } = render(<DrawerHarness />, { container: appRoot })
    const trigger = screen.getByRole('button', { name: 'Открыть панель' })

    trigger.focus()
    fireEvent.click(trigger)

    const closeButton = screen.getByRole('button', { name: 'Закрыть панель' })
    const lastButton = screen.getByRole('button', { name: 'Последнее действие' })
    const backdrop = screen.getByRole('button', { name: 'Закрыть панель по фону' })
    expect(screen.getByRole('dialog', { name: 'Подробности' })).toBeInTheDocument()
    expect(appRoot).toHaveAttribute('inert')
    expect(closeButton).toHaveFocus()
    backdrop.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(closeButton).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(lastButton).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(closeButton).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog', { name: 'Подробности' })).not.toBeInTheDocument()
    expect(appRoot).not.toHaveAttribute('inert')
    expect(trigger).toHaveFocus()

    fireEvent.click(trigger)
    fireEvent.click(screen.getByRole('button', { name: 'Закрыть панель по фону' }))
    expect(screen.queryByRole('dialog', { name: 'Подробности' })).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    unmount()
    appRoot.remove()
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
