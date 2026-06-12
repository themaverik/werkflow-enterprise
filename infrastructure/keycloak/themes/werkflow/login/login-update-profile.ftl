<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Complete Your Profile — Werkflow</title>
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
.wf-form-container { width: 100%; max-width: 400px; }
.wf-form-header { margin-bottom: 32px; }
.wf-form-header h2 { font-size: 24px; font-weight: 700; color: #0f1e2a; letter-spacing: -0.4px; margin-bottom: 6px; }
.wf-form-header p { font-size: 14px; color: #6b7e8c; }
.wf-alert { display: flex; align-items: flex-start; gap: 10px; border-radius: 9px; padding: 11px 14px; margin-bottom: 20px; font-size: 13px; line-height: 1.5; }
.wf-alert-error { background: #fef2f2; border: 1px solid #fecaca; border-left: 3px solid #dc2626; color: #b91c1c; }
.wf-alert-warning { background: #fffbeb; border: 1px solid #fde68a; border-left: 3px solid #d97706; color: #92400e; }
.wf-alert-success { background: #f0fdf4; border: 1px solid #bbf7d0; border-left: 3px solid #16a34a; color: #15803d; }
.wf-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 18px; }
.wf-field label { font-size: 13px; font-weight: 500; color: #374151; }
.wf-field input[type="text"], .wf-field input[type="email"] { width: 100%; padding: 10px 14px; border-radius: 9px; border: 1.5px solid #d1d5db; font-size: 14px; font-family: inherit; color: #111827; background: #fff; outline: none; transition: border-color 0.15s, box-shadow 0.15s; }
.wf-field input[type="text"]:focus, .wf-field input[type="email"]:focus { border-color: #149ba5; box-shadow: 0 0 0 3px rgba(20,155,165,0.13); }
.wf-field input[readonly] { background: #f3f4f6; color: #6b7280; cursor: not-allowed; }
.wf-field-error { font-size: 12px; color: #dc2626; margin-top: 2px; }
.wf-btn-primary { width: 100%; padding: 12px; border-radius: 9px; background: #149ba5; color: #fff; border: none; font-size: 14px; font-weight: 600; font-family: inherit; letter-spacing: 0.01em; cursor: pointer; transition: background 0.15s; }
.wf-btn-primary:hover { background: #107f88; }
.wf-btn-secondary { width: 100%; padding: 12px; border-radius: 9px; background: transparent; color: #374151; border: 1.5px solid #d1d5db; font-size: 14px; font-weight: 500; font-family: inherit; cursor: pointer; margin-top: 10px; }
.wf-btn-secondary:hover { border-color: #9ca3af; }
.wf-form-footer { margin-top: 28px; font-size: 12px; color: #a8b9c4; text-align: center; }
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
            <h1 class="wf-headline">Almost there.<br><span>One last step.</span></h1>
            <p class="wf-tagline">Confirm your account details to complete setup and access your workspace.</p>
        </div>
        <div class="wf-brand-footer">Secured by Keycloak · SSO enabled</div>
    </div>

    <div class="wf-form-panel">
        <div class="wf-form-container">

            <div class="wf-form-header">
                <h2>Complete your profile</h2>
                <p>Review and confirm your account information.</p>
            </div>

            <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="wf-alert wf-alert-${message.type}">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                        <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    <span>${message.summary}</span>
                </div>
            </#if>

            <form action="${url.loginAction}" method="post">

                <#if user.editUsernameAllowed>
                    <div class="wf-field">
                        <label for="username">Username</label>
                        <input type="text" id="username" name="username"
                               value="${(user.username!'')}"
                               <#if !user.editUsernameAllowed>readonly</#if> />
                        <#if messagesPerField.existsError('username')>
                            <span class="wf-field-error">${messagesPerField.get('username')}</span>
                        </#if>
                    </div>
                </#if>

                <div class="wf-field">
                    <label for="email">Email</label>
                    <input type="email" id="email" name="email"
                           value="${(user.email!'')}"
                           <#if !user.editEmailAllowed?? || !user.editEmailAllowed>readonly</#if> />
                    <#if messagesPerField.existsError('email')>
                        <span class="wf-field-error">${messagesPerField.get('email')}</span>
                    </#if>
                </div>

                <div class="wf-field">
                    <label for="firstName">First Name</label>
                    <input type="text" id="firstName" name="firstName"
                           value="${(user.firstName!'')}" />
                    <#if messagesPerField.existsError('firstName')>
                        <span class="wf-field-error">${messagesPerField.get('firstName')}</span>
                    </#if>
                </div>

                <div class="wf-field">
                    <label for="lastName">Last Name</label>
                    <input type="text" id="lastName" name="lastName"
                           value="${(user.lastName!'')}" />
                    <#if messagesPerField.existsError('lastName')>
                        <span class="wf-field-error">${messagesPerField.get('lastName')}</span>
                    </#if>
                </div>

                <button type="submit" class="wf-btn-primary">Save and Continue</button>

                <#if isAppInitiatedAction??>
                    <button type="submit" name="cancel-aia" value="true" class="wf-btn-secondary">Cancel</button>
                </#if>

            </form>

            <p class="wf-form-footer">Protected by Keycloak</p>
        </div>
    </div>

</div>
</body>
</html>
