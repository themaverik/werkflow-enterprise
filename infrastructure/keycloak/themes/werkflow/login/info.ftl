<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Werkflow — Account Setup</title>
    <style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html, body { height: 100%; }
.wf-body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'DM Sans', sans-serif; height: 100vh; overflow: hidden; }
.wf-layout { display: flex; height: 100vh; }
.wf-brand { width: 42%; background: #111c27; position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; padding: 40px 44px; flex-shrink: 0; }
.wf-glow { position: absolute; top: 35%; left: 30%; width: 320px; height: 320px; border-radius: 50%; background: radial-gradient(circle, rgba(20,155,165,0.16) 0%, transparent 70%); pointer-events: none; }
.wf-process-bg { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; mask-image: radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%); -webkit-mask-image: radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%); }
.wf-brand-top { position: relative; }
.wf-brand-copy { position: relative; }
.wf-eyebrow { font-size: 11px; font-weight: 600; color: #149ba5; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 14px; }
.wf-headline { font-size: 30px; font-weight: 700; color: #fff; line-height: 1.25; margin-bottom: 16px; letter-spacing: -0.5px; }
.wf-headline span { color: #149ba5; }
.wf-tagline { font-size: 14px; color: rgba(255,255,255,0.52); line-height: 1.7; max-width: 320px; }
.wf-brand-footer { position: relative; font-size: 11px; color: rgba(255,255,255,0.25); }
.wf-form-panel { flex: 1; background: #f8fafc; display: flex; align-items: center; justify-content: center; padding: 32px; overflow-y: auto; }
.wf-form-container { width: 100%; max-width: 420px; }
.wf-card { background: #fff; border: 1px solid #e5eaee; border-radius: 14px; padding: 36px; }
.wf-card-icon { width: 52px; height: 52px; border-radius: 14px; background: rgba(20,155,165,0.10); display: flex; align-items: center; justify-content: center; margin-bottom: 22px; }
.wf-card h2 { font-size: 20px; font-weight: 700; color: #0f1e2a; letter-spacing: -0.3px; margin-bottom: 10px; }
.wf-card p { font-size: 14px; color: #6b7e8c; line-height: 1.6; margin-bottom: 6px; }
.wf-actions-list { display: flex; flex-direction: column; gap: 8px; margin: 20px 0 28px; }
.wf-action-chip { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 9px; font-size: 13px; font-weight: 500; color: #15803d; }
.wf-action-chip svg { flex-shrink: 0; }
.wf-btn-primary { display: block; width: 100%; padding: 13px; border-radius: 9px; background: #149ba5; color: #fff; border: none; font-size: 14px; font-weight: 600; font-family: inherit; letter-spacing: 0.01em; cursor: pointer; transition: background 0.15s; text-align: center; text-decoration: none; }
.wf-btn-primary:hover { background: #107f88; }
.wf-back-link { display: block; text-align: center; margin-top: 16px; font-size: 13px; color: #149ba5; text-decoration: none; }
.wf-back-link:hover { text-decoration: underline; }
.wf-form-footer { margin-top: 24px; font-size: 12px; color: #a8b9c4; text-align: center; }
@media (max-width: 768px) {
  .wf-body { overflow: auto; }
  .wf-layout { flex-direction: column; height: auto; min-height: 100vh; }
  .wf-brand { width: 100%; min-height: 140px; padding: 28px; justify-content: flex-start; }
  .wf-brand-copy, .wf-brand-footer { display: none; }
  .wf-form-panel { padding: 28px 20px; align-items: flex-start; }
}
    </style>
</head>
<body class="wf-body">
<div class="wf-layout">

    <div class="wf-brand">
        <svg class="wf-process-bg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 1024" preserveAspectRatio="xMidYMid slice">
<defs><marker id="pa" markerWidth="6" markerHeight="6" refX="5" refY="2" orient="auto"><path d="M 0 0 L 6 2 L 0 4 Z" fill="rgba(255,255,255,0.10)"/></marker></defs>
<line x1="133" y1="190" x2="251" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pa)"/>
<line x1="359" y1="190" x2="474" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pa)"/>
<circle cx="118" cy="190" r="15" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="2"/>
<rect x="251" y="167" width="108" height="46" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<path d="M 500,164 L 526,190 L 500,216 L 474,190 Z" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="1.4"/>
<rect x="652" y="88" width="96" height="44" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<rect x="1032" y="167" width="108" height="46" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<circle cx="1300" cy="190" r="15" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="3.2"/>
        </svg>
        <div class="wf-glow"></div>
        <div class="wf-brand-top">
            <img src="${url.resourcesPath}/img/werkflow-logo.png" alt="Werkflow" style="height:100px;width:auto;object-fit:contain;" />
        </div>
        <div class="wf-brand-copy">
            <div class="wf-eyebrow">Enterprise Workflow Platform</div>
            <h1 class="wf-headline">You've been<br><span>invited.</span></h1>
            <p class="wf-tagline">
                Complete the steps below to activate your account and start managing workflows with your team.
            </p>
        </div>
        <div class="wf-brand-footer">Secured by Keycloak · SSO enabled</div>
    </div>

    <div class="wf-form-panel">
        <div class="wf-form-container">
            <div class="wf-card">

                <div class="wf-card-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 13.5a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 2.69h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.68 2.81a2 2 0 0 1-.45 2.11L7.91 9.91a16 16 0 0 0 6.29 6.29l1.58-1.58a2 2 0 0 1 2.11-.45c.91.32 1.85.55 2.81.68a2 2 0 0 1 1.72 2z"/>
                    </svg>
                </div>

                <#if requiredActions?has_content>
                    <#-- Pending state: list of actions to complete -->
                    <h2>Account activation required</h2>
                    <p>To access Werkflow, please complete the following steps:</p>
                    <div class="wf-actions-list">
                        <#list requiredActions as action>
                            <div class="wf-action-chip">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="9 11 12 14 22 4"/>
                                    <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                                </svg>
                                ${msg("requiredAction.${action}")}
                            </div>
                        </#list>
                    </div>
                    <#if skipLink??>
                    <#elseif actionUri?has_content>
                        <a href="${actionUri}" class="wf-btn-primary">Continue to activation</a>
                    </#if>
                <#else>
                    <#-- Success state: all actions complete -->
                    <h2>You're all set</h2>
                    <p style="margin-bottom:24px;">${message.summary} You can now sign in to Werkflow with your new credentials.</p>
                    <#if pageRedirectUri?has_content>
                        <a href="${pageRedirectUri}" class="wf-btn-primary">Go to application</a>
                    <#else>
                        <a href="${url.loginUrl}" class="wf-btn-primary">Go to login</a>
                    </#if>
                </#if>

            </div>

            <p class="wf-form-footer">Protected by Keycloak</p>
        </div>
    </div>

</div>
</body>
</html>
