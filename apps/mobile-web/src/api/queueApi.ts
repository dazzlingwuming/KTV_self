import type { ApiEnvelope, QueueActionRequest, QueueListResponse } from '../types/queue'

export async function fetchQueue(): Promise<ApiEnvelope<QueueListResponse>> {
  const response = await fetch('/api/queue/list')
  return response.json()
}

export async function removeQueueItem(payload: QueueActionRequest): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/queue/remove', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}

export async function moveQueueItemNext(payload: QueueActionRequest): Promise<ApiEnvelope<{ success: boolean }>> {
  const response = await fetch('/api/queue/move-next', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}
