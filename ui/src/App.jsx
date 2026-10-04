import { useEffect, useState } from 'react'
import ChatWindow from './components/ChatWindow.jsx'
import Sidebar from './components/Sidebar.jsx'
import { deleteConversation, listConversations, loadMessages, sendMessage } from './api.js'

const MOBILE_WIDTH = 760

function isMobile() {
  return window.innerWidth <= MOBILE_WIDTH
}

export default function App() {
  const [conversations, setConversations] = useState([])
  const [conversationId, setConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState(null)

  // on a phone the sidebar is a drawer over the chat, so it starts closed
  const [sidebarOpen, setSidebarOpen] = useState(() => !isMobile())

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

  // picking something on a phone should get the drawer out of the way
  function closeOnMobile() {
    if (isMobile()) {
      setSidebarOpen(false)
    }
  }

  function startNewChat() {
    setConversationId(null)
    setMessages([])
    setError(null)
    closeOnMobile()
  }

  async function openConversation(id) {
    setConversationId(id)
    setError(null)
    closeOnMobile()
    try {
      setMessages(await loadMessages(id))
    } catch {
      setError('Could not open that conversation.')
    }
  }

  async function removeConversation(id) {
    try {
      await deleteConversation(id)
      setConversations((current) => current.filter((item) => item.conversationId !== id))
      if (id === conversationId) {
        setConversationId(null)
        setMessages([])
      }
    } catch {
      setError('Could not delete that conversation.')
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
      }
      refreshConversations()
    } catch {
      setError('The backend did not answer. Check that it is running.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className={sidebarOpen ? 'app' : 'app collapsed'}>
      <Sidebar
        conversations={conversations}
        activeId={conversationId}
        onNewChat={startNewChat}
        onSelect={openConversation}
        onDelete={removeConversation}
        onClose={() => setSidebarOpen(false)}
      />

      {/* only visible on a phone: tapping outside the drawer closes it */}
      <div className="backdrop" onClick={() => setSidebarOpen(false)} />

      <ChatWindow
        messages={messages}
        sending={sending}
        error={error}
        onSend={send}
        onToggleSidebar={() => setSidebarOpen((open) => !open)}
      />
    </div>
  )
}
