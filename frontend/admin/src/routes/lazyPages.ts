import { lazy } from 'react'

const loaders = {
  account: () => import('../pages/Account/AccountSettings'),
  accountDeletion: () => import('../pages/Account/AccountDeletionConfirm'),
  comments: () => import('../pages/Comments/Comments'),
  dashboard: () => import('../pages/Dashboard/Dashboard'),
  login: () => import('../pages/Login/Login'),
  moderation: () => import('../pages/Moderation/Moderation'),
  register: () => import('../pages/Register/Register'),
  siteCreate: () => import('../pages/Sites/SiteCreate'),
  siteDetail: () => import('../pages/Sites/SiteDetail'),
  sites: () => import('../pages/Sites/SitesList'),
}

export function preloadRoute(path: string) {
  if (path === '/') return loaders.dashboard()
  if (path === '/sites') return loaders.sites()
  if (path === '/sites/new') return loaders.siteCreate()
  if (path.startsWith('/sites/')) return loaders.siteDetail()
  if (path === '/moderation') return loaders.moderation()
  if (path === '/comments') return loaders.comments()
  if (path === '/account') return loaders.account()
  return Promise.resolve()
}

export const AccountDeletionConfirm = lazy(loaders.accountDeletion)
export const AccountSettings = lazy(loaders.account)
export const Comments = lazy(loaders.comments)
export const Dashboard = lazy(loaders.dashboard)
export const Login = lazy(loaders.login)
export const Moderation = lazy(loaders.moderation)
export const Register = lazy(loaders.register)
export const SiteCreate = lazy(loaders.siteCreate)
export const SiteDetail = lazy(loaders.siteDetail)
export const SitesList = lazy(loaders.sites)
