import { useEffect, useState } from 'react'
import ChatWindow from './components/ChatWindow.jsx'
import Sidebar from './components/Sidebar.jsx'
import { listConversations, loadMessages, sendMessage } from './api.js'

export default function App() {
  const [conversations, setConversations] = useState([])
  const [conversationId, setConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    refreshConversations()
  }, [])

  async function refreshConversations() {
    try {
      setConversations(await listConversations())
    } catch {
      setError('Could not load past conversations. Is the backend running?')
    }
  }

  function startNewChat() {
    setConversationId(null)
    setMessages([])
    setError(null)
  }

  async function openConversation(id) {
    setConversationId(id)
    setError(null)
    try {
      setMessages(await loadMessages(id))
    } catch {
      setError('Could not open that conversation.')
    }
  }

  async function send(text) {
    // show the question straight away, so the UI does not feel stuck while the model thinks
    setMessages((current) => [...current, { role: 'user', text }])
    setSending(true)
    setError(null)

    try {
      const response = await sendMessage(conversationId, text)
      setMessages((current) => [...current, { role: 'model', text: response.reply }])

      if (!conversationId) {
        setConversationId(response.conversationId)
        refreshConversations()
      }
    } catch {
      setError('The backend did not answer. Check that it is running.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="app">
      <Sidebar
        conversations={conversations}
        activeId={conversationId}
        onNewChat={startNewChat}
        onSelect={openConversation}
      />
      <ChatWindow messages={messages} sending={sending} error={error} onSend={send} />
    </div>
  )
}
