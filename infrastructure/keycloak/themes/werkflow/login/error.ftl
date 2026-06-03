<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Error — Werkflow</title>
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
.wf-error-icon { width: 52px; height: 52px; border-radius: 14px; background: #fef2f2; border: 1.5px solid #fecaca; display: flex; align-items: center; justify-content: center; margin-bottom: 24px; }
.wf-form-header { margin-bottom: 12px; }
.wf-form-header h2 { font-size: 22px; font-weight: 700; color: #0f1e2a; letter-spacing: -0.4px; margin-bottom: 8px; }
.wf-error-message { font-size: 14px; color: #6b7e8c; line-height: 1.6; margin-bottom: 28px; }
.wf-divider { height: 1px; background: #e2eaee; margin-bottom: 24px; }
.wf-btn-primary { display: block; width: 100%; padding: 12px; border-radius: 9px; background: #149ba5; color: #fff; border: none; font-size: 14px; font-weight: 600; font-family: inherit; letter-spacing: 0.01em; cursor: pointer; transition: background 0.15s; text-align: center; text-decoration: none; }
.wf-btn-primary:hover { background: #107f88; text-decoration: none; color: #fff; }
.wf-form-footer { margin-top: 20px; font-size: 12px; color: #a8b9c4; text-align: center; }
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
<line x1="111" y1="560" x2="234" y2="560" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<line x1="342" y1="560" x2="462" y2="560" stroke="rgba(255,255,255,0.10)" stroke-width="1.2" marker-end="url(#pf-arr)"/>
<path d="M 514,560 L 577,560 L 577,478 L 640,478" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<path d="M 514,560 L 577,560 L 577,642 L 640,642" fill="none" stroke="rgba(255,255,255,0.055)" stroke-width="1.2" marker-end="url(#pf-arr-s)"/>
<rect x="54" y="161" width="79" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="1.2"/>
<rect x="251" y="161" width="108" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="1.2"/>
<rect x="474" y="161" width="52" height="58" rx="8" fill="rgba(20,155,165,0.12)" stroke="rgba(20,155,165,0.35)" stroke-width="1.2"/>
<rect x="652" y="81" width="96" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="1.2"/>
<rect x="652" y="241" width="96" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="1.2"/>
<rect x="874" y="161" width="158" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="1.2"/>
<rect x="1032" y="161" width="108" height="58" rx="8" fill="rgba(20,155,165,0.08)" stroke="rgba(20,155,165,0.22)" stroke-width="1.2"/>
<rect x="1285" y="161" width="79" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="1.2"/>
<rect x="33" y="531" width="78" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="1.2"/>
<rect x="234" y="531" width="108" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="1.2"/>
<rect x="462" y="531" width="52" height="58" rx="8" fill="rgba(20,155,165,0.12)" stroke="rgba(20,155,165,0.35)" stroke-width="1.2"/>
<rect x="640" y="449" width="96" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="1.2"/>
<rect x="640" y="613" width="96" height="58" rx="8" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="1.2"/>
        </svg>
        <div class="wf-glow"></div>
        <div class="wf-brand-top">
            <svg width="26" height="26" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="32" height="32" rx="8" fill="#149ba5"/>
                <path d="M8 12h4v8H8zM14 8h4v12h-4zM20 14h4v6h-4z" fill="#fff"/>
            </svg>
            <span style="font-size:15px;font-weight:700;color:#fff;letter-spacing:-0.2px;">Werkflow</span>
        </div>
        <div class="wf-brand-copy">
            <div class="wf-eyebrow">Enterprise Platform</div>
            <h1 class="wf-headline">Process-driven<br><span>workflow automation</span></h1>
            <p class="wf-tagline">Built for enterprise teams that need structured, auditable, multi-step approval flows.</p>
        </div>
        <div class="wf-brand-footer">© 2025 Werkflow. All rights reserved.</div>
    </div>

    <!-- Right: Error panel -->
    <div class="wf-form-panel">
        <div class="wf-form-container">

            <div class="wf-error-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 9v4M12 17h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" stroke="#dc2626" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
            </div>

            <div class="wf-form-header">
                <h2>Authentication error</h2>
            </div>

            <p class="wf-error-message">
                <#if message?has_content>
                    ${message.summary?no_esc}
                <#else>
                    An unexpected error occurred during sign-in. Please try again or contact your administrator if the problem persists.
                </#if>
            </p>

            <div class="wf-divider"></div>

            <#if url.loginUrl?has_content>
                <a href="${url.loginUrl}" class="wf-btn-primary">Return to sign in</a>
            </#if>

            <div class="wf-form-footer">Need help? Contact your IT administrator.</div>

        </div>
    </div>

</div>

</body>
</html>
