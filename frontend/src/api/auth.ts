import request from './request'
import { sha256 } from '@/utils/crypto'

interface AuthResponse {
  code: number
  msg?: string
  data: {
    token: string
    userId: number
    username: string
  }
}

interface MeResponse {
  code: number
  msg?: string
  data: {
    id: number
    username: string
  }
}

interface RegisterResponse {
  code: number
  msg?: string
  data: {
    id: number
    username: string
  }
}

export async function login(username: string, password: string) {
  const hashed = await sha256(password)
  const { data } = await request.post<AuthResponse>('/api/auth/login', {
    username,
    password: hashed,
  })
  return data
}

export async function register(username: string, password: string) {
  const hashed = await sha256(password)
  const { data } = await request.post<RegisterResponse>('/api/auth/register', {
    username,
    password: hashed,
  })
  return data
}

export async function logout() {
  const { data } = await request.post('/api/auth/logout')
  return data
}

export async function getMe() {
  const { data } = await request.get<MeResponse>('/api/auth/me')
  return data
}
