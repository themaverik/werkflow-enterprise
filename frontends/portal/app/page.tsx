import Link from "next/link"

export default function HomePage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center p-24">
      <div className="max-w-5xl w-full flex flex-col items-center">
        <div className="flex justify-center mb-12">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/werkflow-logo.png"
            alt="Werkflow"
            style={{ height: '400px', width: 'auto' }}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full">
          {[
            {
              href: '/processes',
              title: 'Process Studio',
              desc: 'Design business workflows visually with drag-and-drop interface',
            },
            {
              href: '/forms',
              title: 'Form Builder',
              desc: 'Create dynamic forms and link them to workflow tasks',
            },
            {
              href: '/tasks',
              title: 'View Tasks',
              desc: 'View and complete your assigned workflow tasks',
            },
          ].map(({ href, title, desc }) => (
            <Link
              key={href}
              href={href}
              className="group rounded-xl border border-gray-200 px-6 py-5 transition-all hover:border-[#4c85b6] hover:shadow-lg hover:shadow-[#4c85b6]/10 shadow-sm bg-white"
            >
              <h2 className="mb-2 text-lg font-semibold flex items-center gap-1" style={{ color: '#4c85b6' }}>
                {title}
                <span className="inline-block transition-transform group-hover:translate-x-1 motion-reduce:transform-none text-base">
                  →
                </span>
              </h2>
              <p className="m-0 text-sm text-gray-500 leading-relaxed">
                {desc}
              </p>
            </Link>
          ))}
        </div>

        <div className="mt-16 flex items-center justify-center gap-3">
          <span className="text-sm text-muted-foreground">Powered by</span>
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 24" className="h-6 opacity-60 hover:opacity-90 transition-opacity">
            <rect width="80" height="24" rx="4" fill="#0A6EC5"/>
            <text x="8" y="17" fontFamily="monospace,sans-serif" fontSize="13" fontWeight="700" fill="white" letterSpacing="0.5">bpmn-io</text>
          </svg>
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 90 24" className="h-6 opacity-60 hover:opacity-90 transition-opacity">
            <rect width="90" height="24" rx="4" fill="#E87B00"/>
            <text x="8" y="17" fontFamily="sans-serif" fontSize="13" fontWeight="700" fill="white" letterSpacing="0.3">Flowable</text>
          </svg>
        </div>
      </div>
    </div>
  )
}
