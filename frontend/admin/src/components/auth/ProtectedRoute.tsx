import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuthStore } from '../../store'

const ProtectedRoute = () => {
  const status = useAuthStore((state) => state.status)
  const location = useLocation()

  if (status === 'checking') {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--bg)' }}>
        <p role="status" aria-live="polite" style={{ color: 'var(--text)' }}>Проверяем доступ…</p>
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

export default ProtectedRoute
