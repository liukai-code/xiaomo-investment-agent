import { Marked } from 'marked'
import DOMPurify from 'dompurify'

const inlineMarked = new Marked()

export function renderInline(text: string): string {
  if (!text) return ''
  return DOMPurify.sanitize(inlineMarked.parseInline(text) as string)
}
