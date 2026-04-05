import type { ApiEnvelope, PlayerStatusResponse } from '../types/player'

export async function fetchPlayerStatus(): Promise<ApiEnvelope<PlayerStatusResponse>> {
  const response = await fetch('/api/player/status')
  return response.json()
}

export async function playNext(): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/player/next', { method: 'POST' })
  return response.json()
}

export async function pausePlayer(): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/player/pause', { method: 'POST' })
  return response.json()
}

export async function resumePlayer(): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/player/resume', { method: 'POST' })
  return response.json()
}

export async function setPlayerVolume(value: number): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/player/volume', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ value }),
  })
  return response.json()
}
