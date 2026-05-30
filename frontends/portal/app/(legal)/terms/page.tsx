import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Terms of Use — Werkflow',
}

const EFFECTIVE_DATE = '30 May 2026'
const CONTACT_EMAIL = 'legal@werkflow.io'

export default function TermsOfUsePage() {
  return (
    <article style={prose}>
      <h1 style={h1Style}>Terms of Use</h1>
      <p style={metaStyle}>Effective date: {EFFECTIVE_DATE} &nbsp;&middot;&nbsp; Version 1.0</p>

      <Section title="1. Acceptance">
        <p>By accessing or using the Werkflow Enterprise Portal (&ldquo;Platform&rdquo;), you agree to be bound by these Terms of Use (&ldquo;Terms&rdquo;). If you do not agree, do not use the Platform. These Terms apply to all users of the Platform, including administrators and end users.</p>
        <p>Access to the Platform is governed by an agreement between Werkflow and your employer or organisation (&ldquo;Customer&rdquo;). These Terms apply to your individual use and do not supersede that agreement.</p>
      </Section>

      <Section title="2. Description of service">
        <p>Werkflow is an enterprise workflow management platform that enables organisations to design, deploy, and manage business processes using BPMN 2.0, DMN decision tables, and form-based task assignment. The Platform includes the portal, workflow engine, admin console, and associated APIs.</p>
      </Section>

      <Section title="3. Authorised use">
        <p>You may use the Platform only:</p>
        <ul>
          <li>for lawful business purposes within the scope authorised by your employer,</li>
          <li>in compliance with all applicable laws and regulations,</li>
          <li>in accordance with any internal policies your employer has established.</li>
        </ul>
        <p>You must not:</p>
        <ul>
          <li>share your credentials or allow others to access the Platform using your account,</li>
          <li>attempt to bypass authentication, authorisation, or tenant-isolation controls,</li>
          <li>use the Platform to process data belonging to a different organisation&apos;s tenant,</li>
          <li>upload or process content that is unlawful, harmful, or infringes third-party rights,</li>
          <li>use the BPMN script-task capability to execute code that has not been approved by your organisation&apos;s administrator,</li>
          <li>conduct penetration testing or security research without prior written consent from Werkflow.</li>
        </ul>
      </Section>

      <Section title="4. Account responsibility">
        <p>You are responsible for maintaining the confidentiality of your session and for all activity that occurs under your account. Notify your system administrator immediately if you suspect unauthorised access. Session tokens expire in accordance with your organisation&apos;s Keycloak configuration.</p>
      </Section>

      <Section title="5. Intellectual property">
        <p>The Platform software, design, and documentation are the intellectual property of Werkflow or its licensors. The Platform includes open-source components; their licences are listed in the <code>LICENSES</code> directory of the distribution.</p>
        <p>Process definitions, forms, and data you create using the Platform belong to your organisation (the Customer) in accordance with the Customer agreement.</p>
      </Section>

      <Section title="6. Data processing">
        <p>Our handling of personal data in connection with your use of the Platform is described in the <a href="/legal/privacy" style={linkStyle}>Privacy Policy</a>. Your organisation acts as a separate data controller for the business data processed through workflows it configures.</p>
      </Section>

      <Section title="7. Availability and support">
        <p>The Platform is provided &ldquo;as is&rdquo; for the purposes of this pre-production deployment. Werkflow does not guarantee uninterrupted or error-free operation. Support, SLAs, and uptime commitments are governed solely by the Customer agreement between your organisation and Werkflow.</p>
      </Section>

      <Section title="8. Limitation of liability">
        <p>To the maximum extent permitted by applicable law, Werkflow and its affiliates, officers, and employees are not liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of, or inability to use, the Platform — including loss of data, business interruption, or loss of profits — even if we have been advised of the possibility of such damages.</p>
        <p>Nothing in these Terms excludes or limits liability for death or personal injury caused by negligence, fraud or fraudulent misrepresentation, or any other liability that cannot be excluded by applicable law.</p>
      </Section>

      <Section title="9. Changes to the Platform and these Terms">
        <p>We may update these Terms at any time. The effective date at the top of this page reflects the latest version. Continued use of the Platform after changes take effect constitutes acceptance of the revised Terms. Material changes affecting Customer agreements require separate notification to the Customer.</p>
      </Section>

      <Section title="10. Governing law">
        <p>These Terms are governed by and construed in accordance with the laws of England and Wales. Any dispute arising under these Terms shall be subject to the exclusive jurisdiction of the courts of England and Wales, unless otherwise agreed in the Customer agreement.</p>
      </Section>

      <Section title="11. Contact">
        <p>Legal enquiries: <a href={`mailto:${CONTACT_EMAIL}`} style={linkStyle}>{CONTACT_EMAIL}</a></p>
      </Section>
    </article>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginBottom: 36 }}>
      <h2 style={h2Style}>{title}</h2>
      {children}
    </section>
  )
}

const prose: React.CSSProperties = {
  color: '#1a2e3a',
  lineHeight: 1.7,
  fontSize: 15,
}

const h1Style: React.CSSProperties = {
  fontSize: 28,
  fontWeight: 700,
  color: '#0f1e2a',
  marginBottom: 4,
  letterSpacing: '-0.4px',
}

const h2Style: React.CSSProperties = {
  fontSize: 17,
  fontWeight: 600,
  color: '#0f1e2a',
  marginBottom: 12,
  paddingBottom: 6,
  borderBottom: '1px solid #e2e8f0',
}

const metaStyle: React.CSSProperties = {
  fontSize: 13,
  color: '#64748b',
  marginBottom: 40,
}

const linkStyle: React.CSSProperties = {
  color: '#149ba5',
  textDecoration: 'underline',
}
