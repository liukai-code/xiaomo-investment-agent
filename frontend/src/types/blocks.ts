export type BlockType =
  | 'heading'
  | 'paragraph'
  | 'code'
  | 'list'
  | 'table'
  | 'blockquote'
  | 'hr'

export interface BaseBlock {
  type: BlockType
  key: string
  closed: boolean
}

export interface HeadingBlock extends BaseBlock {
  type: 'heading'
  level: 1 | 2 | 3 | 4 | 5 | 6
  text: string
}

export interface ParagraphBlock extends BaseBlock {
  type: 'paragraph'
  lines: string[]
}

export interface CodeBlock extends BaseBlock {
  type: 'code'
  language: string
  code: string
}

export interface ListBlock extends BaseBlock {
  type: 'list'
  ordered: boolean
  items: ListItem[]
}

export interface ListItem {
  text: string
  indent: number
  children: ListItem[]
}

export interface TableBlock extends BaseBlock {
  type: 'table'
  header: string[]
  alignments: ('left' | 'center' | 'right')[]
  rows: string[][]
  hasSeparator: boolean
}

export interface BlockquoteBlock extends BaseBlock {
  type: 'blockquote'
  lines: string[]
}

export interface HrBlock extends BaseBlock {
  type: 'hr'
}

export type MarkdownBlock =
  | HeadingBlock
  | ParagraphBlock
  | CodeBlock
  | ListBlock
  | TableBlock
  | BlockquoteBlock
  | HrBlock
