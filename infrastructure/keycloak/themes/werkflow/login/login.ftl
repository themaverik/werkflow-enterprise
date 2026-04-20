<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=social.displaySocialProviders; section>
    <#if section = "header">
        <div class="login-pf-header">
            <div class="login-pf-logo">
                <svg class="werkflow-logo" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="50" cy="50" r="45" fill="#667eea" opacity="0.1"/>
                    <g transform="translate(50,50)">
                        <circle cx="-20" cy="-20" r="8" fill="#667eea"/>
                        <circle cx="20" cy="-20" r="8" fill="#764ba2"/>
                        <circle cx="0" cy="25" r="8" fill="#667eea"/>
                        <line x1="-20" y1="-20" x2="0" y2="25" stroke="#667eea" stroke-width="2"/>
                        <line x1="20" y1="-20" x2="0" y2="25" stroke="#764ba2" stroke-width="2"/>
                    </g>
                </svg>
            </div>
            <div class="login-pf-header-text">
                Werkflow
            </div>
            <div class="login-pf-header-subtitle">
                Enterprise Workflow Management Platform
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
