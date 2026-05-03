<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Sign in — Werkflow</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,600;9..40,700&display=swap" rel="stylesheet">
    <style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html, body { height: 100%; }
.wf-body { font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; height: 100vh; overflow: hidden; }
.wf-layout { display: flex; height: 100vh; }
.wf-brand { width: 42%; background: #111c27; position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; padding: 40px 44px; flex-shrink: 0; }
.wf-geo-bg { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.wf-glow { position: absolute; top: 35%; left: 30%; width: 320px; height: 320px; border-radius: 50%; background: radial-gradient(circle, rgba(20,155,165,0.16) 0%, transparent 70%); pointer-events: none; }
.wf-brand-top { position: relative; display: flex; align-items: center; gap: 10px; }
.wf-logomark { width: 34px; height: 34px; border-radius: 8px; background: #149ba5; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.wf-wordmark { font-size: 18px; font-weight: 700; color: #fff; letter-spacing: -0.3px; }
.wf-brand-copy { position: relative; }
.wf-eyebrow { font-size: 11px; font-weight: 600; color: #149ba5; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 14px; }
.wf-headline { font-size: 32px; font-weight: 700; color: #fff; line-height: 1.25; margin-bottom: 16px; letter-spacing: -0.5px; }
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
.wf-field input[type="text"], .wf-field input[type="password"], .wf-field input[type="email"] { width: 100%; padding: 10px 14px; border-radius: 9px; border: 1.5px solid #d1d5db; font-size: 14px; font-family: inherit; color: #111827; background: #fff; outline: none; transition: border-color 0.15s, box-shadow 0.15s; }
.wf-field input[type="text"]:focus, .wf-field input[type="password"]:focus, .wf-field input[type="email"]:focus { border-color: #149ba5; box-shadow: 0 0 0 3px rgba(20,155,165,0.13); }
.wf-field input[readonly] { background: #f3f4f6; color: #6b7280; cursor: not-allowed; }
.wf-pw-wrapper { position: relative; display: flex; align-items: center; }
.wf-pw-wrapper input { padding-right: 42px !important; }
.wf-pw-toggle { position: absolute; right: 12px; background: none; border: none; cursor: pointer; color: #9ca3af; padding: 0; display: flex; align-items: center; justify-content: center; }
.wf-pw-toggle:hover { color: #6b7280; }
.wf-form-actions { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.wf-remember { display: flex; align-items: center; gap: 7px; font-size: 13px; color: #374151; cursor: pointer; }
.wf-remember input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; accent-color: #149ba5; margin: 0; }
.wf-forgot { font-size: 13px; font-weight: 500; color: #149ba5; text-decoration: none; }
.wf-forgot:hover { text-decoration: underline; }
.wf-btn-primary { width: 100%; padding: 12px; border-radius: 9px; background: #149ba5; color: #fff; border: none; font-size: 14px; font-weight: 600; font-family: inherit; letter-spacing: 0.01em; cursor: pointer; transition: background 0.15s; }
.wf-btn-primary:hover:not(:disabled) { background: #107f88; }
.wf-btn-primary:disabled { background: rgba(20,155,165,0.6); cursor: not-allowed; }
.wf-divider { display: flex; align-items: center; gap: 12px; margin: 24px 0; }
.wf-divider::before, .wf-divider::after { content: ''; flex: 1; height: 1px; background: #e2eaee; }
.wf-divider span { font-size: 12px; color: #a8b9c4; white-space: nowrap; }
.wf-social-list { list-style: none; display: flex; flex-direction: column; gap: 10px; }
.wf-social-btn { display: flex; align-items: center; justify-content: center; gap: 9px; width: 100%; padding: 10px; border-radius: 9px; background: #fff; border: 1.5px solid #e2eaee; color: #374151; font-size: 13px; font-weight: 500; text-decoration: none; transition: border-color 0.15s; }
.wf-social-btn:hover { border-color: #149ba5; color: #149ba5; text-decoration: none; }
.wf-register-link { margin-top: 20px; font-size: 13px; color: #6b7e8c; text-align: center; }
.wf-register-link a { color: #149ba5; font-weight: 500; text-decoration: none; }
.wf-register-link a:hover { text-decoration: underline; }
.wf-form-footer { margin-top: 28px; font-size: 12px; color: #a8b9c4; text-align: center; }
@media (max-width: 768px) {
  .wf-body { overflow: auto; }
  .wf-layout { flex-direction: column; height: auto; min-height: 100vh; }
  .wf-brand { width: 100%; min-height: 160px; padding: 28px; justify-content: flex-start; }
  .wf-brand-copy, .wf-brand-footer { display: none; }
  .wf-form-panel { padding: 28px 20px; align-items: flex-start; }
  .wf-headline { font-size: 26px; }
}
    </style>
</head>
<body class="wf-body">

<div class="wf-layout">

    <!-- Left: Brand panel -->
    <div class="wf-brand">
        <svg class="wf-geo-bg" viewBox="0 0 360 700" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
            <polygon points="0,0 180,0 90,140"        fill="rgba(255,255,255,0.03)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="180,0 360,0 270,140"      fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="90,140 270,140 180,280"   fill="rgba(255,255,255,0.04)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="0,140 90,140 0,280"       fill="rgba(255,255,255,0.02)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="270,140 360,140 360,280"  fill="rgba(255,255,255,0.025)" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="0,280 180,280 90,420"     fill="rgba(255,255,255,0.03)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="180,280 360,280 270,420"  fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="0,420 180,420 90,560"     fill="rgba(255,255,255,0.04)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="180,420 360,420 270,560"  fill="rgba(255,255,255,0.02)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="90,560 270,560 180,700"   fill="rgba(255,255,255,0.03)"  stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="0,560 90,560 0,700"       fill="rgba(255,255,255,0.015)" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="270,560 360,560 360,700"  fill="rgba(255,255,255,0.025)" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <polygon points="160,100 280,60 240,200"   fill="#149ba518"/>
            <polygon points="40,300 140,260 100,400"   fill="#149ba510"/>
            <polygon points="200,380 320,340 280,480"  fill="#149ba514"/>
            <polygon points="60,500 200,480 130,620"   fill="#149ba50d"/>
        </svg>
        <div class="wf-glow"></div>

        <div class="wf-brand-top">
            <div class="wf-logomark">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                    <path d="M2 17l10 5 10-5"/>
                    <path d="M2 12l10 5 10-5"/>
                </svg>
            </div>
            <span class="wf-wordmark">Werkflow</span>
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
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M20 6L9 17l-5-5"/>
                        </svg>
                    </span>
                    BPMN process designer
                </li>
                <li>
                    <span class="wf-feature-icon">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                            <path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/>
                        </svg>
                    </span>
                    Role-based task routing
                </li>
                <li>
                    <span class="wf-feature-icon">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M23 6l-9.5 9.5-5-5L1 18"/>
                        </svg>
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
                <h2>Sign in to your account</h2>
                <p>Use your organisation credentials to continue.</p>
            </div>

            <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="wf-alert wf-alert-${message.type}">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                        <line x1="12" y1="9" x2="12" y2="13"/>
                        <line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    <span>${message.summary}</span>
                </div>
            </#if>

            <#if realm.password>
                <form id="kc-form-login" onsubmit="document.getElementById('kc-login').disabled=true; return true;" action="${url.loginAction}" method="post">

                    <div class="wf-field">
                        <label for="username">
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </label>
                        <input id="username" name="username" type="text"
                            value="${(login.username!'')}"
                            placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>you@company.com<#else>${msg("email")}</#if>"
                            <#if usernameEditDisabled??>readonly</#if>
                            autofocus autocomplete="username" />
                    </div>

                    <div class="wf-field">
                        <label for="password">${msg("password")}</label>
                        <div class="wf-pw-wrapper">
                            <input id="password" name="password" type="password"
                                placeholder="••••••••"
                                autocomplete="current-password" />
                            <button type="button" class="wf-pw-toggle" onclick="togglePassword(this)" aria-label="Toggle password visibility">
                                <svg class="icon-eye" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                    <circle cx="12" cy="12" r="3"/>
                                </svg>
                                <svg class="icon-eye-off" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                                    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                                    <line x1="1" y1="1" x2="23" y2="23"/>
                                </svg>
                            </button>
                        </div>
                    </div>

                    <div class="wf-form-actions">
                        <#if realm.rememberMe && !usernameEditDisabled??>
                            <label class="wf-remember">
                                <input type="checkbox" id="rememberMe" name="rememberMe" <#if login.rememberMe??>checked</#if>>
                                <span>${msg("rememberMe")}</span>
                            </label>
                        <#else>
                            <span></span>
                        </#if>
                        <#if realm.resetPasswordAllowed>
                            <a href="${url.loginResetCredentialsUrl}" class="wf-forgot">${msg("doForgotPassword")}</a>
                        </#if>
                    </div>

                    <button type="submit" id="kc-login" class="wf-btn-primary">
                        ${msg("doLogIn")}
                    </button>

                </form>
            </#if>

            <#if social.providers?has_content>
                <div class="wf-divider"><span>or continue with</span></div>
                <ul class="wf-social-list">
                    <#list social.providers as p>
                        <li>
                            <a href="${p.loginUrl}" id="social-${p.alias}" class="wf-social-btn">
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                                </svg>
                                ${p.displayName}
                            </a>
                        </li>
                    </#list>
                </ul>
            </#if>

            <#if realm.password && realm.registrationAllowed && !usernameEditDisabled??>
                <p class="wf-register-link">
                    ${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a>
                </p>
            </#if>

            <p class="wf-form-footer">Protected by Keycloak</p>

        </div>
    </div>

</div>

<script>
    function togglePassword(btn) {
        var input = document.getElementById('password');
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
