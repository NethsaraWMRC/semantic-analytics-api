// Every call to the Spring Boot backend lives here.
//
// Empty base URL means same origin, which is what local development uses: Vite proxies
// /chat to the backend. Other environments set VITE_API_BASE_URL to an absolute URL.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

async function request(path, options) {
  const response = await fetch(BASE_URL + path, options)
  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}

export function listConversations() {
  return request('/chat/conversations')
}

export function loadMessages(conversationId) {
  return request(`/chat/conversations/${conversationId}`)
}

export function deleteConversation(conversationId) {
  return request(`/chat/conversations/${conversationId}`, { method: 'DELETE' })
}

export function sendMessage(conversationId, message) {
  return request('/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message }),
  })
}
