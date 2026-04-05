import type {
  ApiEnvelope,
  BindSessionRequest,
  BindSessionResponse,
  SessionStatusResponse,
} from '../types/api'

export async function fetchSessionStatus(): Promise<ApiEnvelope<SessionStatusResponse>> {
  const response = await fetch('/api/session/status')
  return response.json()
}

export async function bindSession(payload: BindSessionRequest): Promise<ApiEnvelope<BindSessionResponse>> {
  const response = await fetch('/api/session/bind', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return response.json()
}
