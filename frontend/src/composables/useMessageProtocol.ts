import { computed, type Ref } from 'vue'
import { parseAndValidate, type MessageBlock, type ValidatedMessage } from '@/utils/validateJsonBlocks'

export type { MessageBlock, ValidatedMessage }

export function useMessageProtocol(text: Ref<string>) {
  const parsed = computed<ValidatedMessage>(() => parseAndValidate(text.value))
  return { parsed }
}
