import type { ApiEnvelope, LocalFileStatusResponse } from '../types/local'

export async function fetchLocalFileStatus(songId: string): Promise<ApiEnvelope<LocalFileStatusResponse>> {
  const response = await fetch(`/api/local/file-status?song_id=${encodeURIComponent(songId)}`)
  return response.json()
}

export async function fetchLocalList(): Promise<ApiEnvelope<{ list: LocalFileStatusResponse[] }>> {
  const response = await fetch('/api/local/list')
  return response.json()
}

export async function localOrder(payload: { song_id: string; client_id: string; client_name?: string }) {
  const response = await fetch('/api/local/order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}

export async function localDelete(payload: { song_id: string }) {
  const response = await fetch('/api/local/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}
