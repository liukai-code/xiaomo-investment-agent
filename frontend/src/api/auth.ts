import request from './request'
import { sha256 } from '@/utils/crypto'

interface AuthResponse {
  code: number
  msg?: string
  data: {
    token: string
    userId: number
    email: string
    accountId: string
  }
}

interface MeResponse {
  code: number
  msg?: string
  data: {
    id: number
    email: string
    accountId: string
    freeTokenQuota: number
    freeTokenUsed: number
  }
}

interface RegisterResponse {
  code: number
  msg?: string
  data: {
    id: number
    email: string
    accountId: string
  }
}

export async function login(email: string, password: string) {
  const hashed = await sha256(password)
  const { data } = await request.post<AuthResponse>('/api/auth/login', {
    email,
    password: hashed,
  })
  return data
}

export async function register(email: string, password: string) {
  const hashed = await sha256(password)
  const { data } = await request.post<RegisterResponse>('/api/auth/register', {
    email,
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
