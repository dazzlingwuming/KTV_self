type WsHandlers = {
  onOpen?: () => void
  onClose?: () => void
  onMessage?: (payload: unknown) => void
}

export class WsClient {
  private socket: WebSocket | null = null

  connect(params: { sessionId: string; clientId: string; clientName?: string }, handlers: WsHandlers) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = new URL(`${protocol}://${window.location.host}/ws`)
    url.searchParams.set('session_id', params.sessionId)
    url.searchParams.set('client_id', params.clientId)
    if (params.clientName) url.searchParams.set('client_name', params.clientName)

    this.socket = new WebSocket(url)
    this.socket.onopen = () => handlers.onOpen?.()
    this.socket.onclose = () => handlers.onClose?.()
    this.socket.onmessage = (event) => {
      try {
        handlers.onMessage?.(JSON.parse(event.data))
      } catch {
        handlers.onMessage?.(event.data)
      }
    }
  }

  disconnect() {
    this.socket?.close()
    this.socket = null
  }

  send(payload: unknown) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(typeof payload === 'string' ? payload : JSON.stringify(payload))
    }
  }
}
