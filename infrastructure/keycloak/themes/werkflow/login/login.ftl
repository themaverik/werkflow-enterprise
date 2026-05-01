<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=social.displaySocialProviders; section>
    <#if section = "header">
        <div class="login-pf-header">
            <div class="login-pf-logo">
                <svg class="werkflow-logo" viewBox="0 0 52 52" xmlns="http://www.w3.org/2000/svg">
                    <rect width="52" height="52" rx="13" fill="#149ba5"/>
                    <polyline points="26,10 10,19 26,28 42,19 26,10" fill="none" stroke="#fff" stroke-width="2.8" stroke-linejoin="round" stroke-linecap="round"/>
                    <polyline points="10,34 26,43 42,34" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round"/>
                    <polyline points="10,26.5 26,35.5 42,26.5" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round"/>
                </svg>
            </div>
            <div class="login-pf-header-text">
                Werkflow
            </div>
            <div class="login-pf-header-subtitle">
                Enterprise Workflow Platform
            </div>
        </div>
    <#elseif section = "form">
        <div id="kc-form" <#if realm.password && social.providers??>class="login-pf-form"</#if>>
            <div id="kc-form-wrapper" <#if realm.password && social.providers??>class="col-sm-8"</#if>>
                <#if realm.password>
                    <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                        <div class="form-group">
                            <label for="username" class="sr-only"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                            <input tabindex="1" id="username" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="off" placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>" />
                        </div>

                        <div class="form-group">
                            <label for="password" class="sr-only">${msg("password")}</label>
                            <input tabindex="2" id="password" name="password" type="password" autocomplete="off" placeholder="${msg("password")}" />
                        </div>

                        <div class="form-group form-group-login">
                            <#if realm.rememberMe && !usernameEditDisabled??>
                                <div class="checkbox">
                                    <label>
                                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>> ${msg("rememberMe")}
                                    </label>
                                </div>
                            </#if>
                        </div>

                        <div class="form-group">
                            <div id="kc-form-buttons">
                                <input tabindex="4" class="btn btn-primary" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                            </div>
                        </div>
                    </form>
                </#if>
            </div>
        </div>

        <#if realm.password && social.providers??>
            <div id="kc-social-providers" class="col-sm-4">
                <div class="kc-social-providers">
                    <ul>
                        <#list social.providers as p>
                            <li><a href="${p.loginUrl}" id="social-${p.alias}" class="btn btn-default"><i class="fa fa-${p.displayName?lower_case}"></i> ${p.displayName}</a></li>
                        </#list>
                    </ul>
                </div>
            </div>
        </#if>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !usernameEditDisabled??>
            <div id="kc-registration" class="login-pf-footer">
                <span>${msg("noAccount")} <a tabindex="5" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
            </div>
        </#if>
        <#if realm.password && realm.resetPasswordAllowed>
            <div id="kc-forgot-password" class="login-pf-footer">
                <span><a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a></span>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#if social.displaySocialProviders>
            <div id="kc-social-providers">
                <ul>
                    <#list social.providers as p>
                        <li><a href="${p.loginUrl}" id="social-${p.alias}" class="btn btn-default"><i class="fa fa-${p.displayName?lower_case}"></i> ${p.displayName}</a></li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
