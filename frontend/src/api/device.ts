import axios from 'axios'

export const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

export interface DeviceData {
  id: number
  deviceId: string
  dox: number
  ph: number
  thw: number
  collectTimeStr: string
  createdAt: string
}

export interface ApiResponse<T> {
  code: number
  data: T
  message?: string
}

export function getLatest() {
  return api.get<ApiResponse<DeviceData | null>>('/device/latest')
}

export function getTrend(hours: number = 24) {
  return api.get<ApiResponse<DeviceData[]>>('/device/trend', { params: { hours } })
}
