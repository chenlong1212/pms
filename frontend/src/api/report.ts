import { api, type ApiResponse } from './device'

export type ReportType = 'DAILY' | 'WEEKLY'
export interface ReportRequest { pondId: number; reportType: ReportType; reportDate: string; provider?: string }
export interface MetricSummary { avg: number; min: number; max: number }
export interface ReportPreview {
  pondId: number; pondName: string; fishSpecies: string; reportType: ReportType
  startDate: string; endDate: string
  waterQuality: { sampleCount: number; dox: MetricSummary | null; ph: MetricSummary | null; temperature: MetricSummary | null }
  biomass: { fishCount: number; avgWeightKg: number; biomassKg: number } | null
  feeding: { actualKg: number | null; recommendedKg: number | null; deviationPercent: number | null }
  dataQuality: { ready: boolean; missingFields: string[]; warnings: string[] }
}
export interface ProductionReport {
  id: number; pondId: number; pondName: string; reportType: ReportType
  startDate: string; endDate: string; title: string; status: string
  dataSnapshot: ReportPreview; content: string; modelProvider: string
  modelName: string; createdAt: string
}
interface PageResult<T> { records: T[]; total: number; page: number; size: number }

export const previewReport = (data: ReportRequest) =>
  api.post<ApiResponse<ReportPreview>>('/reports/preview', data)
export const createReport = (data: ReportRequest) =>
  api.post<ApiResponse<ProductionReport>>('/reports', data, { timeout: 90_000 })
export const listReports = (pondId?: number, page = 1, size = 10) =>
  api.get<ApiResponse<PageResult<ProductionReport>>>('/reports', { params: { pondId, page, size } })
export const reportPdfUrl = (id: number) => `/api/reports/${id}/pdf`
