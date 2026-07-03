import { Suspense, type ReactNode } from 'react'
import { createBrowserRouter } from 'react-router-dom'

import ProtectedRoute from '../components/auth/ProtectedRoute'
import Layout from '../components/layout/Layout'
import {
  AccountDeletionConfirm,
  AccountSettings,
  Comments,
  Dashboard,
  Login,
  Moderation,
  Register,
  SiteCreate,
  SiteDetail,
  SitesList,
} from './lazyPages'

function routeElement(element: ReactNode) {
  return (
    <Suspense
      fallback={
        <div className="px-4 py-6 text-sm" style={{ color: 'var(--text)' }}>
          Загрузка раздела...
        </div>
      }
    >
      {element}
    </Suspense>
  )
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: routeElement(<Login />),
  },
  {
    path: '/register',
    element: routeElement(<Register />),
  },
  {
    path: '/account/deletion-confirm',
    element: routeElement(<AccountDeletionConfirm />),
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: routeElement(<Dashboard />) },
          { path: 'sites', element: routeElement(<SitesList />) },
          { path: 'sites/new', element: routeElement(<SiteCreate />) },
          { path: 'sites/:siteId', element: routeElement(<SiteDetail />) },
          { path: 'comments', element: routeElement(<Comments />) },
          { path: 'moderation', element: routeElement(<Moderation />) },
          { path: 'account', element: routeElement(<AccountSettings />) },
        ],
      },
    ],
  },
])
