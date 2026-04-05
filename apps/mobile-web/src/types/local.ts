import type { ApiEnvelope } from './api'

export type { ApiEnvelope }

export interface LocalFileStatusResponse {
  song_id: string
  source_id?: string
  title?: string
  download_status: string
  file_exists: boolean
  file_path?: string
  file_size: number
  is_valid: boolean
  error_code?: string
  error_message?: string
}
