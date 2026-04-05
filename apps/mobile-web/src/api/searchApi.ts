import type { ApiEnvelope, SearchResponse } from '../types/search'

export async function searchSongs(keyword: string): Promise<ApiEnvelope<SearchResponse>> {
  const response = await fetch(`/api/search/bilibili?keyword=${encodeURIComponent(keyword)}`)
  return response.json()
}
