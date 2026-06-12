<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Set Password — Werkflow</title>
    <style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html, body { height: 100%; }
.wf-body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'DM Sans', sans-serif; height: 100vh; overflow: hidden; }
.wf-layout { display: flex; height: 100vh; }
.wf-brand { width: 42%; background: #111c27; position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; padding: 40px 44px; flex-shrink: 0; }
.wf-geo-bg { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.wf-glow { position: absolute; top: 35%; left: 30%; width: 320px; height: 320px; border-radius: 50%; background: radial-gradient(circle, rgba(20,155,165,0.16) 0%, transparent 70%); pointer-events: none; }
.wf-brand-top { position: relative; display: flex; align-items: center; gap: 10px; }
.wf-process-bg { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; mask-image: radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%); -webkit-mask-image: radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%); }
.wf-brand-copy { position: relative; }
.wf-eyebrow { font-size: 11px; font-weight: 600; color: #149ba5; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 14px; }
.wf-headline { font-size: 30px; font-weight: 700; color: #fff; line-height: 1.25; margin-bottom: 16px; letter-spacing: -0.5px; }
.wf-headline span { color: #149ba5; }
.wf-tagline { font-size: 14px; color: rgba(255,255,255,0.52); line-height: 1.7; max-width: 320px; margin-bottom: 28px; }
.wf-features { list-style: none; display: flex; flex-direction: column; gap: 12px; }
.wf-features li { display: flex; align-items: center; gap: 10px; font-size: 13px; color: rgba(255,255,255,0.6); }
.wf-feature-icon { width: 22px; height: 22px; border-radius: 6px; background: rgba(20,155,165,0.16); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
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
.wf-alert-info { background: #eff6ff; border: 1px solid #bfdbfe; border-left: 3px solid #2563eb; color: #1e40af; }
.wf-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 18px; }
.wf-field label { font-size: 13px; font-weight: 500; color: #374151; }
.wf-field input[type="password"] { width: 100%; padding: 10px 14px; border-radius: 9px; border: 1.5px solid #d1d5db; font-size: 14px; font-family: inherit; color: #111827; background: #fff; outline: none; transition: border-color 0.15s, box-shadow 0.15s; }
.wf-field input[type="password"]:focus { border-color: #149ba5; box-shadow: 0 0 0 3px rgba(20,155,165,0.13); }
.wf-pw-wrapper { position: relative; display: flex; align-items: center; }
.wf-pw-wrapper input { padding-right: 42px !important; }
.wf-pw-toggle { position: absolute; right: 12px; background: none; border: none; cursor: pointer; color: #9ca3af; padding: 0; display: flex; align-items: center; justify-content: center; }
.wf-pw-toggle:hover { color: #6b7280; }
.wf-sessions { display: flex; align-items: center; gap: 7px; font-size: 13px; color: #374151; margin-bottom: 22px; cursor: pointer; }
.wf-sessions input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; accent-color: #149ba5; margin: 0; flex-shrink: 0; }
.wf-btn-primary { width: 100%; padding: 12px; border-radius: 9px; background: #149ba5; color: #fff; border: none; font-size: 14px; font-weight: 600; font-family: inherit; letter-spacing: 0.01em; cursor: pointer; transition: background 0.15s; }
.wf-btn-primary:hover { background: #107f88; }
.wf-btn-secondary { width: 100%; padding: 12px; border-radius: 9px; background: transparent; color: #374151; border: 1.5px solid #d1d5db; font-size: 14px; font-weight: 500; font-family: inherit; cursor: pointer; transition: border-color 0.15s; margin-top: 10px; }
.wf-btn-secondary:hover { border-color: #9ca3af; }
.wf-form-footer { margin-top: 28px; font-size: 12px; color: #a8b9c4; text-align: center; }
@media (max-width: 768px) {
  .wf-body { overflow: auto; }
  .wf-layout { flex-direction: column; height: auto; min-height: 100vh; }
  .wf-brand { width: 100%; min-height: 160px; padding: 28px; justify-content: flex-start; }
  .wf-brand-copy, .wf-brand-footer { display: none; }
  .wf-form-panel { padding: 28px 20px; align-items: flex-start; }
}
    </style>
</head>
<body class="wf-body">

<div class="wf-layout">

    <!-- Left: Brand panel -->
    <div class="wf-brand">
        <svg class="wf-process-bg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 1024" preserveAspectRatio="xMidYMid slice">
<defs>
  <marker id="pf-arr" markerWidth="6" markerHeight="6" refX="5" refY="2" orient="auto"><path d="M 0 0 L 6 2 L 0 4 Z" fill="rgba(255,255,255,0.10)"/></marker>
  <marker id="pf-arr-s" markerWidth="6" markerHeight="6" refX="5" refY="2" orient="auto"><path d="M 0 0 L 6 2 L 0 4 Z" fill="rgba(255,255,255,0.055)"/></marker>
</defs>
<line x1="133" y1="190" x2="251" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<line x1="359" y1="190" x2="474" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<path d="M 526,190 L 589,190 L 589,110 L 652,110" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<path d="M 526,190 L 589,190 L 589,270 L 652,270" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<path d="M 748,110 L 811,110 L 811,190 L 874,190" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<path d="M 748,270 L 811,270 L 811,190 L 874,190" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<line x1="926" y1="190" x2="1032" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<line x1="1140" y1="190" x2="1285" y2="190" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<circle cx="118" cy="190" r="15" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="2"/>
<rect x="251" y="167" width="108" height="46" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<path d="M 500,164 L 526,190 L 500,216 L 474,190 Z" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="1.4"/>
<rect x="652" y="88" width="96" height="44" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<rect x="652" y="248" width="96" height="44" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<path d="M 900,164 L 926,190 L 900,216 L 874,190 Z" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<rect x="1032" y="167" width="108" height="46" rx="11" fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.13)" stroke-width="1.4"/>
<circle cx="1300" cy="190" r="15" fill="rgba(255,255,255,0.015)" stroke="rgba(20,155,165,0.42)" stroke-width="3.2"/>
<circle r="3.4" fill="#149ba5">
  <animateMotion dur="18s" begin="-1s" repeatCount="indefinite" path="M 118,190 L 305,190 L 500,190 L 600,190 L 600,110 L 700,110 L 800,110 L 800,190 L 900,190 L 1086,190 L 1300,190"/>
  <animate attributeName="opacity" values="0;0.55;0.55;0" keyTimes="0;0.07;0.9;1" dur="18s" begin="-1s" repeatCount="indefinite"/>
</circle>
        </svg>
        <div class="wf-glow"></div>

        <div class="wf-brand-top">
            <img src="${url.resourcesPath}/img/werkflow-logo.png" alt="Werkflow" style="height:100px;width:auto;object-fit:contain;" />
        </div>

        <div class="wf-brand-copy">
            <div class="wf-eyebrow">Enterprise Workflow Platform</div>
            <h1 class="wf-headline">
                Automate the work.<br>
                <span>Own the outcome.</span>
            </h1>
            <p class="wf-tagline">
                Design, deploy and monitor business processes — procurement,
                onboarding, approvals and more — all in one place.
            </p>
            <ul class="wf-features">
                <li>
                    <span class="wf-feature-icon">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
                    </span>
                    BPMN process designer
                </li>
                <li>
                    <span class="wf-feature-icon">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/></svg>
                    </span>
                    Role-based task routing
                </li>
                <li>
                    <span class="wf-feature-icon">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 6l-9.5 9.5-5-5L1 18"/></svg>
                    </span>
                    Live instance monitoring
                </li>
            </ul>
        </div>

        <div class="wf-brand-footer">Secured by Keycloak · SSO enabled</div>
    </div>

    <!-- Right: Form panel -->
    <div class="wf-form-panel">
        <div class="wf-form-container">

            <div class="wf-form-header">
                <h2>Set your password</h2>
                <p>Choose a strong password to activate your account.</p>
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

            <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">

                <!-- Hidden username for password manager association -->
                <input type="text" id="username" name="username" value="${username!''}" autocomplete="username" readonly style="display:none;" />

                <div class="wf-field">
                    <label for="password-new">New Password</label>
                    <div class="wf-pw-wrapper">
                        <input type="password" id="password-new" name="password-new"
                               autocomplete="new-password" autofocus
                               placeholder="••••••••" />
                        <button type="button" class="wf-pw-toggle" onclick="togglePw('password-new', this)" aria-label="Toggle password visibility">
                            <svg class="icon-eye" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                            </svg>
                            <svg class="icon-eye-off" style="display:none" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                                <line x1="1" y1="1" x2="23" y2="23"/>
                            </svg>
                        </button>
                    </div>
                </div>

                <div class="wf-field">
                    <label for="password-confirm">Confirm Password</label>
                    <div class="wf-pw-wrapper">
                        <input type="password" id="password-confirm" name="password-confirm"
                               autocomplete="new-password"
                               placeholder="••••••••" />
                        <button type="button" class="wf-pw-toggle" onclick="togglePw('password-confirm', this)" aria-label="Toggle confirm password visibility">
                            <svg class="icon-eye" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                            </svg>
                            <svg class="icon-eye-off" style="display:none" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                                <line x1="1" y1="1" x2="23" y2="23"/>
                            </svg>
                        </button>
                    </div>
                </div>

                <label class="wf-sessions">
                    <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked />
                    <span>Sign out from all other devices</span>
                </label>

                <button type="submit" class="wf-btn-primary">Set Password</button>

                <#if isAppInitiatedAction??>
                    <button type="submit" name="cancel-aia" value="true" class="wf-btn-secondary">Cancel</button>
                </#if>

            </form>

            <p class="wf-form-footer">Protected by Keycloak</p>

        </div>
    </div>

</div>

<script>
    function togglePw(inputId, btn) {
        var input = document.getElementById(inputId);
        var eyeOn = btn.querySelector('.icon-eye');
        var eyeOff = btn.querySelector('.icon-eye-off');
        if (input.type === 'password') {
            input.type = 'text';
            eyeOn.style.display = 'none';
            eyeOff.style.display = 'block';
        } else {
            input.type = 'password';
            eyeOn.style.display = 'block';
            eyeOff.style.display = 'none';
        }
    }
</script>

</body>
</html>
