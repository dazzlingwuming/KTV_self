import type { SessionStoreState } from '../types/model'

class SessionStore {
  private state: SessionStoreState = {
    sessionId: '',
    bindStatus: 'unbound',
    deviceName: '',
    baseUrl: '',
    clientId: '',
    clientName: '',
    clientCount: 0,
  }

  get(): SessionStoreState {
    return this.state
  }

  set(next: SessionStoreState) {
    this.state = next
  }
}

export const sessionStore = new SessionStore()
