import { api, type ApiResponse } from './device'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatReply {
  role: string
  content: string
}

export function sendChat(messages: ChatMessage[]) {
  return api.post<ApiResponse<ChatReply>>('/chat', { messages }, { timeout: 90_000 })
}
