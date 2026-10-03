import { useState } from 'react'

export default function Sidebar({ conversations, activeId, onNewChat, onSelect, onDelete }) {
  // the id awaiting confirmation, so a stray click cannot wipe a conversation
  const [confirming, setConfirming] = useState(null)

  function askToDelete(event, id) {
    event.stopPropagation()
    setConfirming(id)
  }

  function confirmDelete(event, id) {
    event.stopPropagation()
    setConfirming(null)
    onDelete(id)
  }

  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="logo">◆</span>
        <span>Analytics</span>
      </div>

      <button className="new-chat" onClick={onNewChat}>
        <span className="plus">+</span> New chat
      </button>

      <nav className="conversations">
        {conversations.length > 0 && <p className="section">Recent</p>}
        {conversations.length === 0 && <p className="empty">No conversations yet</p>}

        {conversations.map((conversation) => {
          const id = conversation.conversationId
          const isConfirming = confirming === id

          return (
            <div
              key={id}
              className={id === activeId ? 'conversation active' : 'conversation'}
              onClick={() => onSelect(id)}
              title={conversation.title}
            >
              <span className="title">{conversation.title}</span>

              {isConfirming ? (
                <span className="confirm">
                  <button className="danger" onClick={(event) => confirmDelete(event, id)}>
                    Delete
                  </button>
                  <button
                    onClick={(event) => {
                      event.stopPropagation()
                      setConfirming(null)
                    }}
                  >
                    Cancel
                  </button>
                </span>
              ) : (
                <button
                  className="delete"
                  aria-label="Delete conversation"
                  onClick={(event) => askToDelete(event, id)}
                >
                  ×
                </button>
              )}
            </div>
          )
        })}
      </nav>

      <p className="footnote">Descriptive questions only</p>
    </aside>
  )
}
