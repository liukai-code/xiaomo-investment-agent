import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import katex from 'katex'

const inlineMarked = new Marked()

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
      parts.push(inlineMarked.parseInline(remaining) as string)
      break
    }
    // Text before math
    if (match.index > 0) {
      parts.push(inlineMarked.parseInline(remaining.slice(0, match.index)) as string)
    }
    // Math part
    parts.push(renderMath(match[1], false))
    remaining = remaining.slice(match.index + match[0].length)
  }

  return DOMPurify.sanitize(parts.join(''), purifyConfig)
}
