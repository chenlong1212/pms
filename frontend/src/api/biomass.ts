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

export function getPonds() {
  return api.get<ApiResponse<Pond[]>>('/biomass/ponds')
}

export function getBiomassTrend(pondId: number, days: number = 30) {
  return api.get<ApiResponse<BiomassTrend>>('/biomass/trend', { params: { pondId, days } })
}
