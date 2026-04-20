// Global event emitter for token expiry events
// Allows API client to signal when 401 (token expired) occurs

type TokenExpiredListener = () => void

let listeners: TokenExpiredListener[] = []

export function onTokenExpired(callback: TokenExpiredListener) {
  listeners.push(callback)

  // Return unsubscribe function
  return () => {
    listeners = listeners.filter(l => l !== callback)
  }
}

export function emitTokenExpired() {
  listeners.forEach(callback => {
    try {
      callback()
    } catch (error) {
      console.error('Error in token expired listener:', error)
    }
  })
}
