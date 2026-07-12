import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AsyncState } from './AsyncState'

describe('AsyncState', () => {
  it('показывает понятное пустое состояние', () => {
    render(<AsyncState empty emptyMessage="Комментариев пока нет"><span>данные</span></AsyncState>)
    expect(screen.getByText('Комментариев пока нет')).toBeInTheDocument()
    expect(screen.queryByText('данные')).not.toBeInTheDocument()
  })

  it('сообщает об ошибке', () => {
    render(<AsyncState error="Сервис временно недоступен"><span>данные</span></AsyncState>)
    expect(screen.getByText('Сервис временно недоступен')).toBeInTheDocument()
  })
})
