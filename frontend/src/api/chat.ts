import { api, type ApiResponse } from './device'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatReply {
  role: string
  content: string
}

export type ModelProvider = 'deepseek' | 'qwen' | 'fanli'

export function sendChat(provider: ModelProvider, messages: ChatMessage[]) {
  return api.post<ApiResponse<ChatReply>>('/chat', { provider, messages }, { timeout: 90_000 })
}
