import { createBrowserRouter } from 'react-router-dom';
import Layout from '../components/layout/Layout';
import Dashboard from '../pages/Dashboard/Dashboard';
import Comments from '../pages/Comments/Comments';
import Users from '../pages/Users/Users';
import Moderation from '../pages/Moderation/Moderation';
import Statistics from '../pages/Statistics/Statistics';
import Settings from '../pages/Settings/Settings';
import Login from '../pages/Login/Login';
import ProtectedRoute from '../components/auth/ProtectedRoute';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <Dashboard /> },
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
