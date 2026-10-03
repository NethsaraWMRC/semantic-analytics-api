// Every call to the Spring Boot backend lives here.

async function request(url, options) {
  const response = await fetch(url, options)
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
