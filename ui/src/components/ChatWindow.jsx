import { useEffect, useRef, useState } from 'react'
import Message from './Message.jsx'

export default function ChatWindow({ messages, sending, error, onSend }) {
  const [draft, setDraft] = useState('')
  const bottom = useRef(null)

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  function submit(event) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || sending) {
      return
    }
    setDraft('')
    onSend(text)
  }

  return (
    <main className="chat">
      <div className="messages">
        {messages.length === 0 && !sending && (
          <div className="welcome">
            <h1>Ask about your data</h1>
            <p>For example: “What were the best selling products last month?”</p>
          </div>
        )}

        {messages.map((message, index) => (
          <Message key={index} role={message.role} text={message.text} />
        ))}

        {sending && (
          <div className="message model thinking">
            <span className="dot" />
            <span className="dot" />
            <span className="dot" />
          </div>
        )}

        {error && <p className="error">{error}</p>}

        <div ref={bottom} />
      </div>

      <form className="composer" onSubmit={submit}>
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Ask a question about your data"
          disabled={sending}
        />
        <button type="submit" disabled={sending || draft.trim() === ''}>
          Send
        </button>
      </form>
    </main>
  )
}
