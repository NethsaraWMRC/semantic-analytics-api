import { useEffect, useRef, useState } from 'react'
import Message from './Message.jsx'

const EXAMPLES = [
  'What were the best selling products last month?',
  'Revenue share by product type over the last 30 days',
  'Top 3 products in each category this quarter',
  'Which sizes are out of stock right now?',
]

export default function ChatWindow({ messages, sending, error, onSend, onToggleSidebar }) {
  const [draft, setDraft] = useState('')
  const bottom = useRef(null)
  const input = useRef(null)

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  function submit(event) {
    event.preventDefault()
    send(draft)
  }

  function send(text) {
    const trimmed = text.trim()
    if (!trimmed || sending) {
      return
    }
    setDraft('')
    onSend(trimmed)
  }

  // Enter sends, Shift+Enter makes a new line
  function onKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit(event)
    }
  }

  function grow(element) {
    if (element) {
      element.style.height = 'auto'
      element.style.height = `${Math.min(element.scrollHeight, 200)}px`
    }
  }

  const empty = messages.length === 0 && !sending

  return (
    <main className="chat">
      <header className="topbar">
        <button className="icon" onClick={onToggleSidebar} aria-label="Toggle sidebar">
          ☰
        </button>
        <span className="topbar-title">Analytics assistant</span>
      </header>

      <div className="messages">
        {empty && (
          <div className="welcome">
            <h1>What would you like to know?</h1>
            <p>Ask about sales, products or stock in plain English.</p>
            <div className="examples">
              {EXAMPLES.map((example) => (
                <button key={example} onClick={() => send(example)}>
                  {example}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message, index) => (
          <Message key={index} role={message.role} text={message.text} />
        ))}

        {sending && (
          <div className="turn model">
            <div className="avatar">◆</div>
            <div className="bubble thinking">
              <span className="dot" />
              <span className="dot" />
              <span className="dot" />
            </div>
          </div>
        )}

        {error && <p className="error">{error}</p>}

        <div ref={bottom} />
      </div>

      <form className="composer" onSubmit={submit}>
        <div className="composer-box">
          <textarea
            ref={(element) => {
              input.current = element
              grow(element)
            }}
            rows={1}
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value)
              grow(event.target)
            }}
            onKeyDown={onKeyDown}
            placeholder="Ask about your data…"
            disabled={sending}
          />
          <button type="submit" disabled={sending || draft.trim() === ''} aria-label="Send">
            ↑
          </button>
        </div>
        <p className="hint">Answers come from your database. Figures are not forecasts.</p>
      </form>
    </main>
  )
}
