import type { ApiEnvelope } from './api'

export type { ApiEnvelope }

export interface CreateOrderRequest {
  client_id: string
  client_name?: string
  source_type: string
  source_id: string
  title: string
  artist?: string
  duration?: number
  cover_url?: string
}

export interface CreateOrderResponse {
  accepted: boolean
  message: string
  queue_id?: string
  song_id?: string
  download_status?: string
}
