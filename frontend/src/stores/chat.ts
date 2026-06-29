import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listConversations,
  createConversation as apiCreateConversation,
  getMessages,
  generateTitle as apiGenerateTitle,
  deleteConversation as apiDeleteConversation,
  type Conversation,
  type ChatMessage,
} from '@/api/conversation'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConvId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isGenerating = ref(false)

  async function loadConversations() {
    const res = await listConversations()
    if (res.code === 1) {
      conversations.value = res.data || []
    }
  }

  async function createConversation(title = '新对话') {
    const res = await apiCreateConversation(title)
    if (res.code === 1) {
      await loadConversations()
      currentConvId.value = res.data.id
      messages.value = []
      return res.data
    }
    return null
  }

  async function switchConversation(id: number) {
    currentConvId.value = id
    const res = await getMessages(id)
    if (res.code === 1 && res.data?.length > 0) {
      messages.value = res.data
    } else {
      messages.value = []
    }
  }

  function addUserMessage(content: string) {
    messages.value.push({
      id: Date.now(),
      role: 'USER',
      content,
      createdAt: new Date().toISOString(),
    })
  }

  function addStreamingAiMessage() {
    const tempMsg: ChatMessage = {
      id: Date.now(),
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
    }
    messages.value.push(tempMsg)
    return tempMsg
  }

  function updateLastAiMessage(content: string) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'ASSISTANT') {
      last.content = content
    }
  }

  async function generateTitle(convId: number) {
    const conv = conversations.value.find((c) => c.id === convId)
    if (!conv || conv.title !== '新对话') return
    const res = await apiGenerateTitle(convId)
    if (res.code === 1 && res.data) {
      await loadConversations()
    }
  }

  function getCurrentConversation() {
    return conversations.value.find((c) => c.id === currentConvId.value)
  }

  async function deleteConversation(convId: number) {
    const res = await apiDeleteConversation(convId)
    if (res.code === 1) {
      const idx = conversations.value.findIndex((c) => c.id === convId)
      conversations.value.splice(idx, 1)
      if (currentConvId.value === convId) {
        if (conversations.value.length > 0) {
          await switchConversation(conversations.value[0].id)
        } else {
          currentConvId.value = null
          messages.value = []
        }
      }
      return true
    }
    return false
  }

  return {
    conversations,
    currentConvId,
    messages,
    isGenerating,
    loadConversations,
    createConversation,
    switchConversation,
    addUserMessage,
    addStreamingAiMessage,
    updateLastAiMessage,
    generateTitle,
    getCurrentConversation,
    deleteConversation,
  }
})
