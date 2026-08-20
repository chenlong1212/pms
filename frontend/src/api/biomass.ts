import { api, type ApiResponse } from './device'

export interface Pond {
  id: number
  name: string
  fishSpecies: string
}

export interface BiomassTrend {
  pondId: number
  pondName: string
  fishSpecies: string
  days: number
  dates: string[]
  biomass: number[]
  count: number[]
  avgWeight: number[]
}

export interface BiomassCorrection {
  pondId: number
  pondName: string
  recordDate: string
  fishCount: number
  avgWeightKg: number
  biomassKg: number
}

export interface BiomassCorrectionRequest {
  pondId: number
  recordDate: string
  fishCount: number
  avgWeightKg: number
}

export interface PondSetup {
  pondId: number
  pondName: string
  fishSpecies: string
  stockDate: string | null
  initialFishCount: number | null
  initialWeightKg: number | null
  harvestDate: string | null
  finalFishCount: number | null
  finalWeightKg: number | null
}

export interface PondSetupRequest {
  pondId: number
  stockDate: string
  initialFishCount: number
  initialWeightKg: number
  harvestDate?: string | null
  finalFishCount?: number | null
  finalWeightKg?: number | null
}

export function getPonds() {
  return api.get<ApiResponse<Pond[]>>('/biomass/ponds')
}

export function getBiomassTrend(pondId: number, days: number = 30) {
  return api.get<ApiResponse<BiomassTrend>>('/biomass/trend', { params: { pondId, days } })
}

export function getPondSetup(pondId: number) {
  return api.get<ApiResponse<PondSetup>>('/biomass/setup', { params: { pondId } })
}

export function savePondSetup(body: PondSetupRequest) {
  return api.put<ApiResponse<PondSetup>>('/biomass/setup', body)
}

export function getBiomassRecord(pondId: number, date: string) {
  return api.get<ApiResponse<BiomassCorrection | null>>('/biomass/record', { params: { pondId, date } })
}

export function correctBiomassRecord(body: BiomassCorrectionRequest) {
  return api.put<ApiResponse<BiomassCorrection>>('/biomass/record', body)
}
