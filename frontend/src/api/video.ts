import { api, type ApiResponse } from './device'

export function getVideoStreamUrl() {
  return api.get<ApiResponse<string | null>>('/video/stream-url')
}
