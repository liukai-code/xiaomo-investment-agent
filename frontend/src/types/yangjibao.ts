export interface QrCode {
  id: string
  url: string
}

export type QrState = 1 | 2

export interface QrCodeState {
  state: QrState
  token?: string
}

export interface UserAccount {
  id: string
  title: string
  count: number
}

export interface AccountCollect {
  hold_cost: number
  today_income: number
  today_income_rate: number
}

export interface FundHoldItem {
  fund_id: string
  code: string
  short_name: string
  money: number
  hold_earn: number
  hold_share: number
  hold_cost: number
}

export interface IndexData {
  name: string
  v: number
  dir: number
}
