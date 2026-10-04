import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

// A wide result table has to scroll on its own, or it pushes the whole page sideways on a phone.
const components = {
  table: ({ node, ...props }) => (
    <div className="table-scroll">
      <table {...props} />
    </div>
  ),
}

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
        <Markdown remarkPlugins={[remarkGfm]} components={components}>
          {text}
        </Markdown>
      </div>
    </div>
  )
}
