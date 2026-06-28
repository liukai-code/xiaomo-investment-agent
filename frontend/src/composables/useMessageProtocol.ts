import { computed, type Ref } from 'vue'

export type MessageType = 'text' | 'card' | 'kpi' | 'table'

export interface ParsedMessage {
  type: MessageType
  title: string
  content: string
  raw: string
}

const TYPE_REGEX = /^<!--type:(text|card|kpi|table)-->\s*\n?/

export function parseMessage(raw: string): ParsedMessage {
  if (!raw) {
    return { type: 'text', title: '', content: '', raw: '' }
  }

  const match = raw.match(TYPE_REGEX)
  const type = (match?.[1] as MessageType) || 'text'
  const content = match ? raw.slice(match[0].length).trim() : raw

  // Extract title from first heading
  const headingMatch = content.match(/^#{1,3}\s+(.+)$/m)
  const title = headingMatch ? headingMatch[1].replace(/^[📊📈📉💡🔍📌🎯💰🏦]\s*/, '') : ''

  return { type, title, content, raw }
}

export function useMessageProtocol(text: Ref<string>) {
  const parsed = computed(() => parseMessage(text.value))
  return { parsed }
}
