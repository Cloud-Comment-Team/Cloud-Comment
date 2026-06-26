import axios from 'axios'

import { API_BASE_URL } from '../config/env'
import { getStoredAuthToken } from '../auth/tokenStorage'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = getStoredAuthToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export { API_BASE_URL }
