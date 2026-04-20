const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025'

export interface MailpitMessage {
  ID: string
  Subject: string
  To: Array<{ Address: string; Name: string }>
  From: { Address: string; Name: string }
}

/**
 * Returns the most recent email in Mailpit, optionally filtered by recipient address.
 * Throws a descriptive error if Mailpit is unreachable.
 */
export async function getLatestEmail(toAddress?: string): Promise<MailpitMessage | null> {
  try {
    const res = await fetch(`${MAILPIT_URL}/api/v1/messages`)
    if (!res.ok) throw new Error(`Mailpit returned ${res.status}`)
    const data = await res.json()
    const messages: MailpitMessage[] = data.messages ?? []
    if (!toAddress) return messages[0] ?? null
    return messages.find(m => m.To?.some(t => t.Address === toAddress)) ?? null
  } catch (err) {
    throw new Error(`Mailpit not reachable at ${MAILPIT_URL}: ${err}`)
  }
}

/**
 * Deletes all messages from Mailpit inbox.
 * Call in test beforeEach to start each test with a clean inbox.
 */
export async function clearMailpit(): Promise<void> {
  try {
    const res = await fetch(`${MAILPIT_URL}/api/v1/messages`, { method: 'DELETE' })
    if (!res.ok) throw new Error(`Mailpit DELETE returned ${res.status}`)
  } catch (err) {
    throw new Error(`Mailpit not reachable at ${MAILPIT_URL}: ${err}`)
  }
}
