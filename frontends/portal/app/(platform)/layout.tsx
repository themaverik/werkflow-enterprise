import { AppShell } from "@/components/layout/app-shell"

export default async function PlatformLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <AppShell>
      {children}
    </AppShell>
  )
}
