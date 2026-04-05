import type { ApiEnvelope } from './api'

export type { ApiEnvelope }

export interface QueueActionRequest {
  queue_id: string
}

export interface QueueItemDto {
  queue_id: string
  song_id: string
  title: string
  source_id: string
  download_status: string
  queue_status: string
  skip_reason?: string
  error_message?: string
  play_mode: string
  position: number
  ordered_by_client_id: string
  ordered_by_client_name?: string
}

export interface QueueListResponse {
  current?: QueueItemDto
  upcoming: QueueItemDto[]
  items: QueueItemDto[]
}
