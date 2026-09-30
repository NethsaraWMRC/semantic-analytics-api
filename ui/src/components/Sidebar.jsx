export default function Sidebar({ conversations, activeId, onNewChat, onSelect }) {
  return (
    <aside className="sidebar">
      <button className="new-chat" onClick={onNewChat}>
        + New chat
      </button>

      <nav className="conversations">
        {conversations.length === 0 && <p className="empty">No conversations yet.</p>}

        {conversations.map((conversation) => (
          <button
            key={conversation.conversationId}
            className={conversation.conversationId === activeId ? 'conversation active' : 'conversation'}
            onClick={() => onSelect(conversation.conversationId)}
            title={conversation.title}
          >
            {conversation.title}
          </button>
        ))}
      </nav>
    </aside>
  )
}
