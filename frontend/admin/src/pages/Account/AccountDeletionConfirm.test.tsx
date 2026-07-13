import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'

const accountApi = vi.hoisted(() => ({ confirmAccountDeletion: vi.fn() }))
const csrfApi = vi.hoisted(() => ({ clearCsrfCredentials: vi.fn() }))

vi.mock('../../api/account', () => accountApi)
vi.mock('../../api/csrf', () => csrfApi)
vi.mock('../../components/auth/AuthFormShell', () => ({
  AuthFormShell: ({ children, title }: { children: React.ReactNode; title: string }) => (
    <main><h1>{title}</h1>{children}</main>
  ),
}))

import { useAuthStore } from '../../store'
import AccountDeletionConfirm from './AccountDeletionConfirm'

beforeEach(() => {
  accountApi.confirmAccountDeletion.mockReset()
  csrfApi.clearCsrfCredentials.mockReset()
})

it('после подтверждения удаления очищает auth-store и CSRF только в памяти', async () => {
  const clearAuth = vi.fn()
  accountApi.confirmAccountDeletion.mockResolvedValue(undefined)
  useAuthStore.setState({ clearAuth })

  render(
    <MemoryRouter initialEntries={['/account/deletion-confirm?token=confirmation-token']}>
      <Routes>
        <Route path="/account/deletion-confirm" element={<AccountDeletionConfirm />} />
      </Routes>
    </MemoryRouter>,
  )

  expect(await screen.findByText('Аккаунт удален')).toBeVisible()
  expect(accountApi.confirmAccountDeletion).toHaveBeenCalledWith('confirmation-token')
  expect(csrfApi.clearCsrfCredentials).toHaveBeenCalledTimes(1)
  expect(clearAuth).toHaveBeenCalledTimes(1)
})
