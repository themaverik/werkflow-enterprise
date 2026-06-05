import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Privacy Policy — Werkflow',
}

const EFFECTIVE_DATE = '30 May 2026'
const CONTACT_EMAIL = 'support@werkflow.cloud'

export default function PrivacyPolicyPage() {
  return (
    <article style={prose}>
      <h1 style={h1Style}>Privacy Policy</h1>
      <p style={metaStyle}>Effective date: {EFFECTIVE_DATE} &nbsp;&middot;&nbsp; Version 1.0</p>

      <Section title="1. Who we are">
        <p>Werkflow (&ldquo;we&rdquo;, &ldquo;us&rdquo;, &ldquo;our&rdquo;) operates the Werkflow Enterprise Portal, a workflow management platform for organisations. For the purposes of data protection law, Werkflow acts as the <strong>data controller</strong> for personal data processed when you use this platform.</p>
        <p>Contact: <a href={`mailto:${CONTACT_EMAIL}`} style={linkStyle}>{CONTACT_EMAIL}</a></p>
      </Section>

      <Section title="2. Personal data we process">
        <p>We process the following categories of personal data:</p>
        <ul>
          <li><strong>Identity &amp; account data:</strong> name, email address, organisational role — provided by your employer via Keycloak.</li>
          <li><strong>Usage data:</strong> pages visited, workflow actions initiated, task completions — logged for internal analytics and audit purposes.</li>
          <li><strong>Technical data:</strong> IP address, browser type, session identifiers — collected automatically.</li>
          <li><strong>Process data:</strong> information you enter into workflow forms (e.g. purchase requisitions, leave requests) — content varies by your employer&apos;s configuration.</li>
        </ul>
        <p>We do not process special-category data (health, biometric, etc.) unless your employer explicitly configures a workflow that requires it, in which case your employer acts as a separate data controller for that data.</p>
      </Section>

      <Section title="3. How and why we process your data">
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={thStyle}>Purpose</th>
              <th style={thStyle}>Legal basis (GDPR Art. 6)</th>
            </tr>
          </thead>
          <tbody>
            <Tr purpose="Providing the platform and executing workflows" basis="Performance of contract (Art. 6(1)(b))" />
            <Tr purpose="Authentication and access control" basis="Legitimate interests (Art. 6(1)(f)) — securing the platform" />
            <Tr purpose="Audit trail and compliance logging" basis="Legal obligation (Art. 6(1)(c)) / Legitimate interests" />
            <Tr purpose="Analytics — understanding how the platform is used" basis="Consent (Art. 6(1)(a)) — managed via cookie settings" />
            <Tr purpose="Security monitoring and incident response" basis="Legitimate interests (Art. 6(1)(f))" />
          </tbody>
        </table>
      </Section>

      <Section title="4. Data retention">
        <ul>
          <li><strong>Account and identity data:</strong> retained for the duration of your employer&apos;s contract, plus 30 days for deletion processing.</li>
          <li><strong>Process / workflow data:</strong> retained according to the retention policy configured by your employer (data controller for this data).</li>
          <li><strong>Audit logs:</strong> retained for 12 months, then archived or deleted.</li>
          <li><strong>Session data:</strong> retained for the duration of the session; auth tokens expire per Keycloak configuration.</li>
          <li><strong>Analytics data:</strong> retained for up to 24 months in aggregate, anonymised form.</li>
        </ul>
      </Section>

      <Section title="5. Who we share data with">
        <p>We do not sell your personal data. We may share it with the following sub-processors:</p>
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={thStyle}>Sub-processor</th>
              <th style={thStyle}>Purpose</th>
              <th style={thStyle}>Location</th>
            </tr>
          </thead>
          <tbody>
            <tr style={trStyle}>
              <td style={tdStyle}>Cloud infrastructure provider (TBC)</td>
              <td style={tdStyle}>Hosting and storage</td>
              <td style={tdStyle}>EU / EEA</td>
            </tr>
            <tr style={trStyle}>
              <td style={tdStyle}>Keycloak (self-hosted)</td>
              <td style={tdStyle}>Identity and authentication</td>
              <td style={tdStyle}>Same host as platform</td>
            </tr>
            <tr style={trStyle}>
              <td style={tdStyle}>OpenBao (self-hosted)</td>
              <td style={tdStyle}>Secret / credential management</td>
              <td style={tdStyle}>Same host as platform</td>
            </tr>
          </tbody>
        </table>
        <p>If data is transferred outside the EU/EEA, it will be protected by Standard Contractual Clauses (SCCs) or an equivalent adequacy mechanism.</p>
      </Section>

      <Section title="6. Your GDPR rights">
        <p>If you are located in the EU/EEA or UK, you have the following rights:</p>
        <ul>
          <li><strong>Right of access (Art. 15):</strong> request a copy of the personal data we hold about you.</li>
          <li><strong>Right to rectification (Art. 16):</strong> request correction of inaccurate data.</li>
          <li><strong>Right to erasure (Art. 17):</strong> request deletion of your data where there is no lawful reason to retain it.</li>
          <li><strong>Right to restriction of processing (Art. 18):</strong> request that we limit the processing of your personal data in certain circumstances, for example while a rectification or objection request is being considered.</li>
          <li><strong>Right to data portability (Art. 20):</strong> receive your data in a machine-readable format.</li>
          <li><strong>Right to object (Art. 21):</strong> object to processing based on legitimate interests or for analytics purposes.</li>
          <li><strong>Rights related to automated decision-making (Art. 22):</strong> not to be subject to a decision based solely on automated processing, including profiling, that produces legal or similarly significant effects concerning you. Where your employer configures a workflow that includes automated decisions (for example, DMN decision tables that automatically approve or reject a request), you may request human review, express your point of view, and contest the decision by contacting your employer or us.</li>
          <li><strong>Right to withdraw consent:</strong> withdraw consent for analytics cookies at any time via <a href="/legal/cookies" style={linkStyle}>Cookie Settings</a>. Withdrawing consent does not affect the lawfulness of processing carried out before the withdrawal.</li>
          <li><strong>Right to lodge a complaint:</strong> you have the right to lodge a complaint with a supervisory authority, in particular in the EU Member State of your habitual residence, your place of work, or the place of the alleged infringement. In the United Kingdom, the supervisory authority is the Information Commissioner&apos;s Office (ICO, <a href="https://ico.org.uk" style={linkStyle} target="_blank" rel="noopener noreferrer">ico.org.uk</a>).</li>
        </ul>
        <p>To exercise your rights, email <a href={`mailto:${CONTACT_EMAIL}`} style={linkStyle}>{CONTACT_EMAIL}</a>. We will respond within one month of receipt of your request, and may extend that period by up to two further months where necessary, taking into account the complexity and number of requests.</p>
      </Section>

      <Section title="7. California residents — CCPA / CPRA rights">
        <p>If you are a California resident, you have additional rights under the California Consumer Privacy Act (CCPA) as amended by the CPRA:</p>
        <ul>
          <li><strong>Right to know:</strong> request disclosure of the categories and specific pieces of personal information we have collected about you.</li>
          <li><strong>Right to delete:</strong> request deletion of personal information we have collected about you, subject to certain exceptions.</li>
          <li><strong>Right to correct:</strong> request correction of inaccurate personal information.</li>
          <li><strong>Right to opt out of sale or sharing:</strong> we do not sell or share your personal information for cross-context behavioural advertising. To confirm this or exercise your rights, email <a href={`mailto:${CONTACT_EMAIL}`} style={linkStyle}>{CONTACT_EMAIL}</a> with subject &ldquo;CCPA Request&rdquo;.</li>
          <li><strong>Right to limit use of sensitive personal information:</strong> where applicable, you may limit our use of sensitive personal information to that necessary for providing the service.</li>
          <li><strong>Right to non-discrimination:</strong> we will not discriminate against you for exercising any of your CCPA rights.</li>
        </ul>
        <p>We do not knowingly collect personal information from individuals under 16 years of age.</p>
        <p><strong>Shine the Light:</strong> California Civil Code §1798.83 permits California residents to request information about our disclosure of personal information to third parties for direct marketing purposes. We do not make such disclosures.</p>
      </Section>

      <Section title="8. Cookies">
        <p>We use cookies and similar technologies. You can manage your preferences at any time via <a href="/legal/cookies" style={linkStyle}>Cookie Settings</a>. See our <a href="/legal/cookies" style={linkStyle}>Cookie Policy</a> for full details.</p>
      </Section>

      <Section title="9. Security">
        <p>We implement technical and organisational measures to protect your personal data, including TLS encryption in transit, AES-256 encryption at rest for secrets, Keycloak-based access control, and tenant-level data isolation in the workflow engine. We maintain a vulnerability disclosure programme — see <a href="/security" style={linkStyle}>SECURITY.md</a> for details.</p>
      </Section>

      <Section title="10. Changes to this policy">
        <p>We may update this Privacy Policy from time to time. The effective date at the top of this page will reflect the most recent revision. Material changes will be communicated via the platform or email.</p>
      </Section>

      <Section title="11. Contact us">
        <p>Data protection enquiries: <a href={`mailto:${CONTACT_EMAIL}`} style={linkStyle}>{CONTACT_EMAIL}</a></p>
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

function Tr({ purpose, basis }: { purpose: string; basis: string }) {
  return (
    <tr style={trStyle}>
      <td style={tdStyle}>{purpose}</td>
      <td style={tdStyle}>{basis}</td>
    </tr>
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

const tableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: 14,
  marginBottom: 16,
}

const thStyle: React.CSSProperties = {
  textAlign: 'left',
  fontWeight: 600,
  padding: '8px 12px',
  background: '#f1f5f9',
  borderBottom: '2px solid #e2e8f0',
  color: '#374151',
}

const trStyle: React.CSSProperties = {
  borderBottom: '1px solid #e2e8f0',
}

const tdStyle: React.CSSProperties = {
  padding: '8px 12px',
  verticalAlign: 'top',
  color: '#374151',
}
