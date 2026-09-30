import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

// The model answers in markdown: bold figures, bullet lists and tables.
// Rendering it is what turns "**Total:** 27,018,117.30" into real formatting.
export default function Message({ role, text }) {
  if (role === 'user') {
    return <div className="message user">{text}</div>
  }

  return (
    <div className="message model">
      <Markdown remarkPlugins={[remarkGfm]}>{text}</Markdown>
    </div>
  )
}
