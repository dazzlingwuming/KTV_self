export interface ApiEnvelope<T> {
  code: number
  message: string
  data?: T
}

export interface BindSessionRequest {
  session_id: string
  client_id?: string
  client_name?: string
  sign: string
}

export interface SessionStatusResponse {
  session_id: string
  bind_status: string
  device_name: string
  client_count: number
  clients: SessionClientResponse[]
  web_url?: string
  qr_payload?: string
}

export interface BindSessionResponse {
  session_id: string
  bind_status: string
  client_id: string
  client_count: number
  clients: SessionClientResponse[]
}

export interface SessionClientResponse {
  client_id: string
  client_name?: string
  connected_at: number
  last_seen_at: number
}
