import type { ApiEnvelope } from './api'

export type { ApiEnvelope }

export interface SearchSongItem {
  source_type: string
  source_id: string
  title: string
  artist?: string
  duration: number
  cover_url?: string
}

export interface SearchResponse {
  list: SearchSongItem[]
}
