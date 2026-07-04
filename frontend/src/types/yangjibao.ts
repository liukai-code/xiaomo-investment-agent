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
  cost_money: number
  hold_day: string
  category: string
  market_type: string
}

export interface IndexData {
  name: string
  v: number
  dir: number
}

export interface FundValuation {
  fund_id: string
  dwjz: number       // 单位净值
  rzzl: number       // 日涨幅 (%)
  vgszzl: number     // 估值涨幅 (%)
  jzrq: string       // 净值日期
}

export interface MarketRankingItem {
  name: string       // 板块名称
  change_rate: number // 涨跌幅 (%)
}

export interface DayInfo {
  day: string
  is_market_day: boolean
  is_hk_market_day: boolean
  last_marketday: string
}
