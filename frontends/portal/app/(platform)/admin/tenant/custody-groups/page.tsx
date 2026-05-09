import { redirect } from 'next/navigation'

export default function CustodyGroupsRedirect() {
  redirect('/admin/tenant/custody-mappings')
}
