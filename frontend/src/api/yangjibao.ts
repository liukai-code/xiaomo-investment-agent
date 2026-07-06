import axios from 'axios'
import request from './request'
import type { QrCode, QrCodeState, UserAccount, AccountCollect, FundHoldItem, IndexData, FundValuation, DayInfo } from '@/types/yangjibao'

const marketClient = axios.create({
  baseURL: '/yjb-market-api',
  timeout: 15000,
})

function extractData<T>(resp: any): T {
  const body = resp.data
  if (!body || body.code !== 1) {
    const detail = body ? JSON.stringify(body) : '响应为空'
    console.error('[YJB] extractData 失败:', detail)
    throw new Error(body?.msg || body?.message || `请求失败: ${detail}`)
  }
  return body.data
}

// ==================== QR 登录（后端代理） ====================

export async function getQrCode(): Promise<QrCode> {
  const resp = await request.get('/api/yjb/qr-code')
  return extractData<QrCode>(resp)
}

export async function getQrCodeState(qrId: string): Promise<QrCodeState> {
  const resp = await request.get(`/api/yjb/qr-state/${qrId}`)
  return extractData<QrCodeState>(resp)
}

// ==================== Token 管理 ====================

export async function saveYjbToken(token: string): Promise<void> {
  await request.post('/api/yjb/token', { token })
}

export async function checkYjbStatus(): Promise<{ loggedIn: boolean }> {
  const resp = await request.get('/api/yjb/status')
  return extractData(resp)
}

// ==================== 数据同步 ====================

export interface SyncResult {
  accounts: UserAccount[]
  accountCollect: AccountCollect
  holdings: FundHoldItem[]
  selectedAccountId: string
}

export async function syncHoldings(accountId?: string): Promise<SyncResult> {
  const resp = await request.post('/api/yjb/sync', null, {
    params: accountId ? { accountId } : {},
  })
  return extractData<SyncResult>(resp)
}

// ==================== 读取已同步数据 ====================

export async function getHoldings(): Promise<{ holdings: FundHoldItem[]; accountCollect: AccountCollect | null }> {
  const resp = await request.get('/api/yjb/holdings')
  return extractData(resp)
}

// ==================== 行情数据（公开 API，前端直连） ====================

export async function getIndexData(): Promise<IndexData[]> {
  const resp = await marketClient.get('/market/v1/quote/index-data')
  const body = resp.data
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败')
  }
  const data = body.data
  return Array.isArray(data) ? data : []
}

export async function getFundValuations(fundIds: string[]): Promise<FundValuation[]> {
  if (!fundIds.length) return []
  try {
    const resp = await request.post('/api/yjb/valuations', { fundIds })
    const body = resp.data
    if (body.code !== 1) {
      console.warn('[YJB] 基金估值接口返回异常:', body.msg)
      return []
    }
    const list = Array.isArray(body.data) ? body.data : []
    return list.map((item: any) => ({
      fund_id: String(item.fund_id ?? ''),
      dwjz: Number(item.dwjz) || 0,
      rzzl: Number(item.rzzl) || 0,
      vgszzl: Number(item.vgszzl) || 0,
      jzrq: String(item.jzrq ?? ''),
    }))
  } catch (err) {
    console.warn('[YJB] 基金估值接口请求失败:', err)
    return []
  }
}

export async function getDayInfo(): Promise<DayInfo | null> {
  try {
    const resp = await marketClient.get('/wxapi/day_info')
    const body = resp.data
    if (body.code !== 200) return null
    return body.data
  } catch {
    return null
  }
}
