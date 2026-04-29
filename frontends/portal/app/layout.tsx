import type { Metadata } from "next"
import { Roboto } from "next/font/google"
import { NextIntlClientProvider } from "next-intl"
import { getMessages } from "next-intl/server"
import { Toaster } from "sonner"
import "./globals.css"
import { Providers } from "./providers"

const roboto = Roboto({ subsets: ["latin"], weight: ["400", "500", "700"] })

export const metadata: Metadata = {
  title: "Werkflow Portal",
  description: "Enterprise workflow management portal",
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  const messages = await getMessages()

  return (
    <html lang="en">
      <body className={roboto.className}>
        <NextIntlClientProvider messages={messages}>
          <Providers>{children}</Providers>
          <Toaster richColors position="top-right" />
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
