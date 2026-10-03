import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

// The model answers in markdown: bold figures, bullet lists and tables.
// Rendering it is what turns "**Total:** 27,018,117.30" into real formatting.
export default function Message({ role, text }) {
  if (role === 'user') {
    return (
      <div className="turn user">
        <div className="bubble">{text}</div>
      </div>
    )
  }

  return (
    <div className="turn model">
      <div className="avatar">◆</div>
      <div className="bubble">
        <Markdown remarkPlugins={[remarkGfm]}>{text}</Markdown>
      </div>
    </div>
  )
}
