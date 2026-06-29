import { lazy } from 'react'

export const Comments = lazy(() => import('../pages/Comments/Comments'))
export const Dashboard = lazy(() => import('../pages/Dashboard/Dashboard'))
export const Login = lazy(() => import('../pages/Login/Login'))
export const Moderation = lazy(() => import('../pages/Moderation/Moderation'))
export const Register = lazy(() => import('../pages/Register/Register'))
export const SiteCreate = lazy(() => import('../pages/Sites/SiteCreate'))
export const SiteDetail = lazy(() => import('../pages/Sites/SiteDetail'))
export const SitesList = lazy(() => import('../pages/Sites/SitesList'))
