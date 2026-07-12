import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import katex from 'katex'

const inlineMarked = new Marked()

// 预处理 **bold** 和 *italic*：marked 在 CJK 字符 + 引号组合下无法识别 emphasis 边界
const boldRe = /(?<!\*)\*\*(?!\*)(.+?)(?<!\*)\*\*(?!\*)/g
const italicRe = /(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g

function preprocessEmphasis(text: string): string {
  return text.replace(boldRe, '<strong>$1</strong>').replace(italicRe, '<em>$1</em>')
}

const purifyConfig: DOMPurify.Config = {
  ADD_TAGS: ['math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'mfrac', 'msqrt', 'annotation'],
  ADD_ATTR: ['mathvariant', 'xmlns'],
}

export function renderMath(tex: string, display: boolean): string {
  try {
    return katex.renderToString(tex, { displayMode: display, throwOnError: false })
  } catch {
    return display ? `<pre>${tex}</pre>` : `<code>${tex}</code>`
  }
}

export function renderInline(text: string): string {
  if (!text) return ''

  // Extract inline math $...$ (not $$)
  const parts: string[] = []
  let remaining = text
  const mathRe = /(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)/

  while (remaining.length > 0) {
    const match = remaining.match(mathRe)
    if (!match || match.index === undefined) {
      parts.push(inlineMarked.parseInline(preprocessEmphasis(remaining)) as string)
      break
    }
    // Text before math
    if (match.index > 0) {
      parts.push(inlineMarked.parseInline(preprocessEmphasis(remaining.slice(0, match.index))) as string)
    }
    // Math part
    parts.push(renderMath(match[1], false))
    remaining = remaining.slice(match.index + match[0].length)
  }

  return DOMPurify.sanitize(parts.join(''), purifyConfig)
}
