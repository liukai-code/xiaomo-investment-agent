import { computed, onUnmounted, type Ref } from 'vue'
import type {
  MarkdownBlock,
  CodeBlock,
  ListBlock,
  ListItem,
  TableBlock,
  MathBlock,
} from '@/types/blocks'

// --- djb2 hash ---

function hash(s: string): string {
  let h = 5381
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) + h + s.charCodeAt(i)) & 0xffffffff
  }
  return (h >>> 0).toString(16).padStart(8, '0')
}

function blockKey(type: string, sig: string): string {
  return `${type}-${hash(sig.slice(0, 40))}`
}

// --- parseBlocks ---

export function parseBlocks(text: string): MarkdownBlock[] {
  if (!text) return []

  const lines = text.split('\n')
  const blocks: MarkdownBlock[] = []
  let paragraphLines: string[] = []
  let state: 'NORMAL' | 'IN_CODE' | 'IN_MATH' = 'NORMAL'
  let codeBlock: CodeBlock | null = null
  let codeFenceLen = 0
  let listBlock: ListBlock | null = null
  let blockquoteLines: string[] = []

  // table accumulation
  let mathLines: string[] = []
  let tableHeader: string[] = []
  let tableAlignments: ('left' | 'center' | 'right')[] = []
  let tableRows: string[][] = []
  let tableHasSeparator = false

  function flushParagraph(closed: boolean) {
    if (paragraphLines.length > 0) {
      blocks.push({
        type: 'paragraph',
        lines: [...paragraphLines],
        closed,
        key: blockKey('p', paragraphLines[0] || ''),
      })
      paragraphLines = []
    }
  }

  function flushBlockquote(closed: boolean) {
    if (blockquoteLines.length > 0) {
      blocks.push({
        type: 'blockquote',
        lines: [...blockquoteLines],
        closed,
        key: blockKey('bq', blockquoteLines[0] || ''),
      })
      blockquoteLines = []
    }
  }

  function flushList(closed: boolean) {
    if (listBlock) {
      listBlock.closed = closed
      blocks.push(listBlock)
      listBlock = null
    }
  }

  function flushTable(closed: boolean) {
    if (tableHeader.length > 0 && tableHasSeparator) {
      blocks.push({
        type: 'table',
        header: tableHeader,
        alignments: tableAlignments,
        rows: tableRows,
        hasSeparator: tableHasSeparator,
        closed,
        key: blockKey('table', tableHeader[0] || ''),
      })
    } else if (tableHeader.length > 0) {
      // no separator seen — emit as paragraph lines
      const rawLines = [tableHeader.join(' | ')]
      for (const row of tableRows) {
        rawLines.push(row.join(' | '))
      }
      blocks.push({
        type: 'paragraph',
        lines: rawLines,
        closed,
        key: blockKey('p', rawLines[0] || ''),
      })
    }
    tableHeader = []
    tableAlignments = []
    tableRows = []
    tableHasSeparator = false
  }

  function parseTableCells(line: string): string[] {
    return line
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((c) => c.trim())
  }

  function parseAlignments(line: string): ('left' | 'center' | 'right')[] {
    return line
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((c) => {
        const cell = c.trim()
        if (cell.startsWith(':') && cell.endsWith(':')) return 'center'
        if (cell.endsWith(':')) return 'right'
        return 'left'
      })
  }

  function isSeparatorRow(line: string): boolean {
    return /^[\|\s:-]+$/.test(line) && line.includes('-')
  }

  function addItemToList(items: ListItem[], item: ListItem, depth: number): void {
    if (depth === 0) {
      items.push(item)
      return
    }
    const last = items[items.length - 1]
    if (!last) {
      items.push(item)
      return
    }
    if (!last.children) last.children = []
    addItemToList(last.children, item, depth - 1)
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // === IN CODE STATE ===
    if (state === 'IN_CODE') {
      const closeMatch = line.match(/^(`{3,})\s*$/)
      if (closeMatch && closeMatch[1].length >= codeFenceLen) {
        codeBlock!.closed = true
        codeBlock!.key = blockKey('code', codeBlock!.language + codeBlock!.code)
        blocks.push(codeBlock!)
        codeBlock = null
        state = 'NORMAL'
      } else {
        if (codeBlock!.code) codeBlock!.code += '\n'
        codeBlock!.code += line
      }
      continue
    }

    // === IN MATH STATE ===
    if (state === 'IN_MATH') {
      if (/^\$\$\s*$/.test(line)) {
        const tex = mathLines.join('\n')
        blocks.push({
          type: 'math',
          tex,
          display: true,
          closed: true,
          key: blockKey('math', tex),
        })
        mathLines = []
        state = 'NORMAL'
      } else {
        mathLines.push(line)
      }
      continue
    }

    // === NORMAL STATE ===

    // Empty line
    if (line.trim() === '') {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      continue
    }

    // HR
    if (/^(\*{3,}|-{3,}|_{3,})\s*$/.test(line)) {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      blocks.push({ type: 'hr', closed: true, key: 'hr' })
      continue
    }

    // Heading
    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      const level = headingMatch[1].length as 1 | 2 | 3 | 4 | 5 | 6
      blocks.push({
        type: 'heading',
        level,
        text: headingMatch[2],
        closed: true,
        key: blockKey(`h${level}`, headingMatch[2]),
      })
      continue
    }

    // Inline heading: text followed by # heading without preceding newline
    const inlineHeadingMatch = line.match(/^(.*?)\s*(#{1,6})\s+(.+)$/)
    if (inlineHeadingMatch && inlineHeadingMatch[1].length > 0) {
      const before = inlineHeadingMatch[1].trim()
      if (before) {
        flushParagraph(true)
        paragraphLines.push(before)
        flushParagraph(true)
      }
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      const level = inlineHeadingMatch[2].length as 1 | 2 | 3 | 4 | 5 | 6
      blocks.push({
        type: 'heading',
        level,
        text: inlineHeadingMatch[3],
        closed: true,
        key: blockKey(`h${level}`, inlineHeadingMatch[3]),
      })
      continue
    }

    // Display math $$...$$
    if (/^\$\$\s*$/.test(line)) {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      mathLines = []
      state = 'IN_MATH'
      continue
    }
    // Single-line display math $$...$$
    const inlineMathMatch = line.match(/^\$\$(.+)\$\$\s*$/)
    if (inlineMathMatch) {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      blocks.push({
        type: 'math',
        tex: inlineMathMatch[1],
        display: true,
        closed: true,
        key: blockKey('math', inlineMathMatch[1]),
      })
      continue
    }

    // Code fence
    const fenceMatch = line.match(/^(`{3,})(.*)$/)
    if (fenceMatch) {
      flushParagraph(true)
      flushBlockquote(true)
      flushList(true)
      flushTable(true)
      codeFenceLen = fenceMatch[1].length
      codeBlock = {
        type: 'code',
        language: fenceMatch[2].trim(),
        code: '',
        closed: false,
        key: '',
      }
      state = 'IN_CODE'
      continue
    }

    // Table row detection
    if (line.startsWith('|') && (line.endsWith('|') || line.includes('|'))) {
      // potential table
      if (tableHeader.length === 0) {
        // first row — could be header
        flushParagraph(true)
        flushBlockquote(true)
        flushList(true)
        tableHeader = parseTableCells(line)
        continue
      } else if (tableHeader.length > 0 && !tableHasSeparator && isSeparatorRow(line)) {
        // separator row
        tableAlignments = parseAlignments(line)
        tableHasSeparator = true
        continue
      } else if (tableHeader.length > 0) {
        // body row
        tableRows.push(parseTableCells(line))
        continue
      }
    }

    // If we had table accumulation but hit a non-table line, flush
    if (tableHeader.length > 0) {
      flushTable(true)
    }

    // List item
    const listMatch = line.match(/^(\s*)([-*+]|\d+\.)\s+(.*)$/)
    if (listMatch) {
      flushParagraph(true)
      flushBlockquote(true)
      const indent = listMatch[1].length
      const marker = listMatch[2]
      const text = listMatch[3]
      const ordered = /\d+\./.test(marker)
      const depth = Math.floor(indent / 2)

      if (!listBlock || listBlock.ordered !== ordered) {
        flushList(true)
        listBlock = {
          type: 'list',
          ordered,
          items: [],
          closed: false,
          key: blockKey(`list-${ordered ? 'o' : 'u'}`, text),
        }
      }

      const item: ListItem = { text, indent: depth, children: [] }
      addItemToList(listBlock.items, item, depth)
      continue
    }

    // If we had list accumulation but hit a non-list line, flush
    if (listBlock) {
      flushList(true)
    }

    // Blockquote
    const bqMatch = line.match(/^>\s?(.*)$/)
    if (bqMatch) {
      flushParagraph(true)
      flushList(true)
      blockquoteLines.push(bqMatch[1])
      continue
    }

    // If we had blockquote accumulation but hit a non-bq line, flush
    if (blockquoteLines.length > 0) {
      flushBlockquote(true)
    }

    // Default: paragraph line
    paragraphLines.push(line)
  }

  // === POST-LOOP: finalize unclosed blocks ===
  if (state === 'IN_CODE' && codeBlock) {
    codeBlock.closed = false
    codeBlock.key = blockKey('code', codeBlock.language + codeBlock.code)
    blocks.push(codeBlock)
  }

  if (state === 'IN_MATH') {
    const tex = mathLines.join('\n')
    blocks.push({
      type: 'math',
      tex,
      display: true,
      closed: false,
      key: blockKey('math', tex),
    })
  }

  if (state === 'NORMAL') {
    flushParagraph(false)
    flushBlockquote(false)
    flushList(false)
    flushTable(false)
  }

  return blocks
}

// --- useMarkdownBlocks composable ---

export function useMarkdownBlocks(text: Ref<string>) {
  const blocks = computed<MarkdownBlock[]>(() => parseBlocks(text.value))
  return { blocks }
}

// --- useRafThrottle composable ---

export function useRafThrottle() {
  let rafId: number | null = null
  let pendingCallback: (() => void) | null = null

  function schedule(callback: () => void) {
    pendingCallback = callback
    if (rafId !== null) return

    rafId = requestAnimationFrame(() => {
      rafId = null
      if (pendingCallback) {
        pendingCallback()
        pendingCallback = null
      }
    })
  }

  function flush() {
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    if (pendingCallback) {
      pendingCallback()
      pendingCallback = null
    }
  }

  function cancel() {
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    pendingCallback = null
  }

  onUnmounted(cancel)

  return { schedule, flush, cancel }
}
