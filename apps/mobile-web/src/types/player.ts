import type { ApiEnvelope } from './api'

export type { ApiEnvelope }

export interface PlayerStatusResponse {
  play_status: string
  current_song_id?: string
  title?: string
  current_time_ms: number
  duration_ms: number
  volume: number
  mode: string
  error_message?: string
}
