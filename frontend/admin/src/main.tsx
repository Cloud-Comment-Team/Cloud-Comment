import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AuthBootstrap } from './components/auth/AuthBootstrap'
import { router } from './routes'
import { ThemeProvider } from './theme'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <AuthBootstrap>
        <RouterProvider router={router} />
      </AuthBootstrap>
    </ThemeProvider>
  </StrictMode>,
)
