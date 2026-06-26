import { createBrowserRouter } from 'react-router-dom';
import Login from '../pages/Login/Login';
import Register from '../pages/Register/Register';

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
    element: <Login />, // временно редирект на логин
  },
]);