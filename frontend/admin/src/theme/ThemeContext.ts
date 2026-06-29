import { createContext } from 'react'

export type ThemeMode = 'light' | 'dark'

export type ThemeRevealOrigin = {
  x: number
  y: number
}

export type ThemeContextValue = {
  theme: ThemeMode
  setTheme: (theme: ThemeMode, origin?: ThemeRevealOrigin) => void
  toggleTheme: (origin?: ThemeRevealOrigin) => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)
