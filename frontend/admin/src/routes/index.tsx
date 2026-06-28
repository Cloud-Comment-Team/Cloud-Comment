import { createBrowserRouter } from 'react-router-dom';
import Layout from '../components/layout/Layout';
import Dashboard from '../pages/Dashboard/Dashboard';
import Comments from '../pages/Comments/Comments';
import Users from '../pages/Users/Users';
import Moderation from '../pages/Moderation/Moderation';
import Statistics from '../pages/Statistics/Statistics';
import Settings from '../pages/Settings/Settings';
import SitesList from '../pages/Sites/SitesList';
import SiteCreate from '../pages/Sites/SiteCreate';
import SiteDetail from '../pages/Sites/SiteDetail';
import Login from '../pages/Login/Login';
import Register from '../pages/Register/Register';
import ProtectedRoute from '../components/auth/ProtectedRoute';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/register',
    element: <Register />,
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <Dashboard /> },
          { path: 'sites', element: <SitesList /> },
          { path: 'sites/new', element: <SiteCreate /> },
          { path: 'sites/:siteId', element: <SiteDetail /> },
          { path: 'comments', element: <Comments /> },
          { path: 'users', element: <Users /> },
          { path: 'moderation', element: <Moderation /> },
          { path: 'statistics', element: <Statistics /> },
          { path: 'settings', element: <Settings /> },
        ],
      },
    ],
  },
]);
