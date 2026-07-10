import { api, type ApiResponse } from './device'
import type { Pond } from './biomass'

export type { Pond }

export interface FeedingRecord {
  id: number
  pondId: number
  feedDate: string
  feedTotalKg: string
  remark: string | null
}

export interface FeedingRecordRequest {
  pondId: number
  feedDate: string
  feedTotalKg: number
  remark?: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface FeedingPlan {
  mealsPerDay: number
  description: string
  amountsKg: number[]
}

export interface FeedingStrategy {
  pondId: number
  pondName: string
  fishSpecies: string
  biomassKg: number | null
  avgWeightKg: number | null
  dailyRate: number | null
  dailyFeedKg: number | null
  plans: FeedingPlan[]
  summaryText: string
  available: boolean
}

export function getPonds() {
  return api.get<ApiResponse<Pond[]>>('/feeding/ponds')
}

export function getFeedingRecords(pondId: number, page = 1, size = 20) {
  return api.get<ApiResponse<PageResult<FeedingRecord>>>('/feeding/records', {
    params: { pondId, page, size },
  })
}

export function createFeedingRecord(body: FeedingRecordRequest) {
  return api.post<ApiResponse<FeedingRecord>>('/feeding/records', body)
}

export function updateFeedingRecord(id: number, body: FeedingRecordRequest) {
  return api.put<ApiResponse<FeedingRecord>>(`/feeding/records/${id}`, body)
}

export function deleteFeedingRecord(id: number) {
  return api.delete<ApiResponse<null>>(`/feeding/records/${id}`)
}

export function getFeedingStrategy(pondId: number) {
  return api.get<ApiResponse<FeedingStrategy>>('/feeding/strategy', {
    params: { pondId },
  })
}

export function buildPlanRemark(plan: FeedingPlan): string {
  if (plan.mealsPerDay === 1) {
    return `按投喂策略直接投喂，共 ${plan.amountsKg[0]} kg`
  }
  if (plan.mealsPerDay === 2) {
    return `今日分批投喂（2餐）：早 ${plan.amountsKg[0]} kg，晚 ${plan.amountsKg[1]} kg`
  }
  return `今日分批投喂（3餐）：早 ${plan.amountsKg[0]} kg，中 ${plan.amountsKg[1]} kg，晚 ${plan.amountsKg[2]} kg`
}
