import axios from 'axios'
import CryptoJS from 'crypto-js'
import type { QrCode, QrCodeState, UserAccount, AccountCollect, FundHoldItem, IndexData } from '@/types/yangjibao'

const BASE_URL = '/yjb-api'
const SECRET = 'YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc'
const TOKEN_KEY = 'yjb_token'

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const marketClient = axios.create({
  baseURL: '/yjb-market-api',
  timeout: 15000,
})

function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

function buildHeaders(path: string): Record<string, string> {
  const token = getToken()
  const timestamp = Math.floor(Date.now() / 1000).toString()
  const signPath = path.split('?')[0]
  const sign = CryptoJS.MD5('' + signPath + token + timestamp + SECRET).toString()
  return {
    'Request-Time': timestamp,
    'Request-Sign': sign,
    'Authorization': token,
  }
}

function extractData<T>(resp: any): T {
  const body = resp.data
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export async function getQrCode(): Promise<QrCode> {
  const path = '/qr_code'
  const resp = await client.get(path, { headers: buildHeaders(path) })
  return extractData<QrCode>(resp)
}

export async function getQrCodeState(qrId: string): Promise<QrCodeState> {
  const path = `/qr_code_state/${qrId}`
  const resp = await client.get(path, { headers: buildHeaders(path) })
  return extractData<QrCodeState>(resp)
}

export async function getUserAccounts(): Promise<UserAccount[]> {
  const path = '/user_account'
  const resp = await client.get(path, { headers: buildHeaders(path) })
  const data = extractData<any>(resp)
  return data.list || data
}

export async function getAccountCollect(): Promise<AccountCollect> {
  const path = '/account_collect'
  const resp = await client.get(path, { headers: buildHeaders(path) })
  const data = extractData<any>(resp)
  return data.account_data?.[0] || data
}

export async function getFundHoldings(accountId: string): Promise<FundHoldItem[]> {
  const path = '/fund_hold'
  const resp = await client.get(path, {
    headers: buildHeaders(path),
    params: { account_id: accountId },
  })
  const data = extractData<any>(resp)
  return Array.isArray(data) ? data : []
}

export async function getIndexData(): Promise<IndexData[]> {
  const resp = await marketClient.get('/market/v1/quote/index-data')
  const data = extractData<any>(resp)
  return Array.isArray(data) ? data : []
}
