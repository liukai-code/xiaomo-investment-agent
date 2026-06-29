export interface MessageBlock {
  type: 'text' | 'title' | 'kpi' | 'table' | 'card' | 'warning'
  content: string
  data?: KpiData[] | TableData
}

export interface KpiData {
  label: string
  value: string
  trend?: 'up' | 'down' | 'neutral'
}

export interface TableData {
  headers: string[]
  rows: string[][]
}

export interface ValidatedMessage {
  blocks: MessageBlock[]
  isJson: boolean
}

const VALID_TYPES = new Set(['text', 'title', 'kpi', 'table', 'card', 'warning'])

function cleanJsonString(raw: string): string {
  let s = raw.trim()
  // Strip markdown code fences
  s = s.replace(/^```(?:json)?\s*\n?/i, '').replace(/\n?```\s*$/i, '')
  // Strip HTML comments (old protocol remnants)
  s = s.replace(/<!--.*?-->/gs, '')
  // Strip leading text before first {
  const firstBrace = s.indexOf('{')
  if (firstBrace > 0) s = s.slice(firstBrace)
  // Strip trailing text after last }
  const lastBrace = s.lastIndexOf('}')
  if (lastBrace >= 0 && lastBrace < s.length - 1) {
    s = s.slice(0, lastBrace + 1)
  }
  // Fix trailing commas before ] or }
  s = s.replace(/,\s*([}\]])/g, '$1')
  return s
}

function makeFallbackBlock(raw: string): MessageBlock[] {
  const cleaned = raw
    .replace(/<!--.*?-->/gs, '')
    .replace(/^```(?:json)?\s*\n?/i, '')
    .replace(/\n?```\s*$/i, '')
    .trim()
  if (!cleaned) return [{ type: 'text', content: '(empty)' }]
  return [{ type: 'text', content: cleaned }]
}

function validateBlocks(blocks: unknown): MessageBlock[] {
  if (!Array.isArray(blocks)) return []
  return blocks
    .filter((b): b is Record<string, unknown> => {
      if (!b || typeof b !== 'object') return false
      return VALID_TYPES.has(b.type as string)
    })
    .map((b) => {
      const block: MessageBlock = {
        type: b.type as MessageBlock['type'],
        content: typeof b.content === 'string' ? b.content : '',
      }
      if (b.data !== undefined) {
        block.data = b.data as KpiData[] | TableData
      }
      return block
    })
}

export function parseAndValidate(raw: string): ValidatedMessage {
  if (!raw || !raw.trim()) {
    return { blocks: [{ type: 'text', content: '(empty)' }], isJson: false }
  }

  // Quick check: does it look like JSON?
  const hasJsonStructure = raw.includes('"blocks"') || (raw.trimStart().startsWith('{') && raw.includes('"type"'))

  if (!hasJsonStructure) {
    // Legacy markdown fallback
    return { blocks: makeFallbackBlock(raw), isJson: false }
  }

  const cleaned = cleanJsonString(raw)

  try {
    const parsed = JSON.parse(cleaned)
    const blocks = validateBlocks(parsed.blocks ?? parsed)
    if (blocks.length > 0) {
      return { blocks, isJson: true }
    }
    // Parsed but no valid blocks — latch to JSON path to avoid flashing raw text
    return { blocks: [], isJson: true }
  } catch {
    // JSON parse failed — try partial extraction
    const partialBlocks = tryExtractPartialBlocks(cleaned)
    if (partialBlocks.length > 0) {
      return { blocks: partialBlocks, isJson: true }
    }
    // JSON detected but incomplete — latch to JSON path, wait for more blocks
    return { blocks: [], isJson: true }
  }
}

function tryExtractPartialBlocks(json: string): MessageBlock[] {
  // Try to extract complete blocks from partial JSON
  // Pattern: find each complete {"type":"...","content":"..."} object
  const blockRegex = /\{\s*"type"\s*:\s*"(text|title|kpi|table|card|warning)"\s*,\s*"content"\s*:\s*"((?:[^"\\]|\\.)*)"\s*(?:,\s*"data"\s*:\s*(\[[\s\S]*?\]|\{[\s\S]*?\}))?\s*\}/g
  const blocks: MessageBlock[] = []
  let match: RegExpExecArray | null

  while ((match = blockRegex.exec(json)) !== null) {
    try {
      const block: MessageBlock = {
        type: match[1] as MessageBlock['type'],
        content: JSON.parse(`"${match[2]}"`), // unescape
      }
      if (match[3]) {
        try {
          block.data = JSON.parse(match[3]) as KpiData[] | TableData
        } catch {
          // data parse failed, skip data
        }
      }
      blocks.push(block)
    } catch {
      // block parse failed, skip
    }
  }

  return blocks
}
