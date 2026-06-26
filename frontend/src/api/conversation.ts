import request from './request'

interface Result<T> {
  code: number
  msg?: string
  data: T
}

export interface Conversation {
  id: number
  title: string
  userId: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  role: 'SYSTEM' | 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  toolName?: string
  toolCallId?: string
  createdAt: string
}

export async function listConversations() {
  const { data } = await request.get<Result<Conversation[]>>('/agent/conversation/list')
  return data
}

export async function createConversation(title = '新对话') {
  const { data } = await request.post<Result<Conversation>>(
    `/agent/conversation?title=${encodeURIComponent(title)}`,
  )
  return data
}

export async function getMessages(convId: number) {
  const { data } = await request.get<Result<ChatMessage[]>>(
    `/agent/conversation/${convId}/messages`,
  )
  return data
}

export async function generateTitle(convId: number) {
  const { data } = await request.post<Result<string>>(
    `/agent/conversation/${convId}/generate-title`,
  )
  return data
}
