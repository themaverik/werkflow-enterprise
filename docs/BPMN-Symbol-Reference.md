# BPMN Symbol Reference

**Werkflow Platform — MVP Release**
**Engine:** Flowable 7.2 + Spring Boot
**Designer:** bpmn-js (stock Modeler) + Werkflow custom properties panel

This document is the exhaustive catalog of BPMN 2.0 elements with Werkflow's exact support level, any custom capability layered on top, and end-to-end testability status. It is the authoritative reference for client conversations and internal engineering decisions.

---

## Support Level Vocabulary

| Label | Meaning |
|---|---|
| **FULL** | First-class designer authoring + engine execution + tested |
| **PARTIAL** | Designer can author it OR engine executes it, but not both fully supported; or capability is limited |
| **DEPRECATED** | Engine still passes it through, but Werkflow policy prohibits new use; cite the ADR |
| **QUARANTINED** | Validator hard-rejects at deploy time; cannot run in Werkflow (cite ADR + class) |
| **NOT SUPPORTED** | Not in designer palette, not validated, not tested; behaviour undefined |
| **NEEDS VERIFICATION** | Claim is plausible from BPMN spec + Flowable docs but not verified in Werkflow's codebase |

## E2E Testability Vocabulary

| Label | Meaning |
|---|---|
| **PLAYWRIGHT** | Testable end-to-end via portal UI flow with existing Playwright suite |
| **PLAYWRIGHT (short)** | Testable via Playwright with reduced timer duration substituted for the full duration |
| **ENGINE-JUNIT** | Only feasible at engine unit/integration level (timing, concurrency, chaos scope) |
| **NOT-TESTABLE-CI** | Manual testing or chaos engineering only; cannot be automated in CI |

---

## 1. Events

### 1.1 Start Events

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> | Process begins; no trigger | **FULL** | `flowable:formKey` attribute links a form-js form; `formKey` select dropdown in properties panel (`flowable-properties-provider.ts`). Tenant-scoped `getStartForm` (session 40 fix) ensures form resolution is tenant-isolated. **Terminology note:** the `formKey` extension is NOT a BPMN trigger — the symbol stays a thin-circle none start regardless of whether a form is attached. Form must exist at deploy time (atomically seeded by `ProcessExampleDeployer` or pre-authored via the portal Form Designer); a missing key surfaces as the "No Start Form" runtime error. In Werkflow docs and demos, prefer **"User-initiated start (form-linked)"** over the ambiguous shorthand "None start (form)". | **PLAYWRIGHT** | Used in all 4 seeded examples. Covered by `03-start-process.spec.ts`, `26-workflow-leave-request.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><rect x="11" y="13" width="14" height="10" rx="1"/><polyline points="11,13 18,19 25,13"/></svg> | Triggered by a named message | **PARTIAL** | `messageRef` is authored by stock bpmn-js; no Werkflow-specific panel. Flowable engine handles message correlation by name. No Werkflow webhook-to-message bridge is wired in the portal today. | **ENGINE-JUNIT** | NEEDS VERIFICATION: message-start correlation via REST has not been smoke-tested in the Playwright suite. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="8"/><line x1="18" y1="10" x2="18" y2="18"/><line x1="18" y1="18" x2="23" y2="18"/></svg> | Triggered at a point in time or interval | **PARTIAL** | bpmn-js authors timer definitions; engine executes via Flowable's job scheduler. No custom designer panel. | **ENGINE-JUNIT** | Long-duration timers (> 30 s) are excluded from CI. Short-timer substitution possible in unit tests only. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polygon points="18,10 25,26 11,26"/></svg> | Triggered by a named signal | **PARTIAL** | `signalRef` authored by stock bpmn-js. Engine executes signal catch; Werkflow provides a `TenantAwareSignalService` for tenant-scoped dispatch. Signal events must be modelled in BPMN — do NOT use bare `runtimeService.signalEventReceived()` from Java (no-op against tenant-scoped subscriptions). | **ENGINE-JUNIT** | Covered by `SignalTenantIsolationTest` at engine level. No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><rect x="12" y="12" width="12" height="12" rx="1"/><line x1="12" y1="16" x2="24" y2="16"/><line x1="12" y1="20" x2="24" y2="20"/></svg> | Triggered when a condition becomes true | **PARTIAL** | bpmn-js authors conditional definitions. Flowable 7.2 supports conditional events. No custom panel. | **ENGINE-JUNIT** | Proven executing per M4.13 audit. No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polyline points="14,12 18,18 16,21 20,21 18,25 22,18 20,18 24,12"/></svg> | Catches a thrown BPMN error inside an event subprocess | **PARTIAL** | Supported by Flowable 7.2. Designer can author inside event subprocess boundary. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no test confirms error-start-in-event-subprocess deploy + execution in Werkflow. |

### 1.2 Intermediate Catching Events

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><rect x="11" y="13" width="14" height="10" rx="1"/><polyline points="11,13 18,19 25,13"/></svg> | Pauses until a named message arrives | **PARTIAL** | `messageRef` authored by stock bpmn-js; `flowable:formKey` and `triggerProcess` selectable via properties panel (`isFormCapableElement` + `isTriggerProcessCapable` logic). Webhook-to-message correlation not wired to a named connector bridge yet. | **ENGINE-JUNIT** | NEEDS VERIFICATION: message correlation integration not confirmed end-to-end. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><circle cx="18" cy="18" r="8"/><line x1="18" y1="10" x2="18" y2="18"/><line x1="18" y1="18" x2="23" y2="18"/></svg> | Pauses until a duration, date, or cycle | **PARTIAL** | bpmn-js authors timer definitions; Flowable job executor handles scheduling. No custom panel. | **PLAYWRIGHT (short)** | Short ISO 8601 duration (e.g., PT2S) substituted for full durations in integration tests. No existing Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polygon points="18,10 25,26 11,26"/></svg> | Pauses until a named signal fires | **FULL** | `signalRef` authored by stock bpmn-js. Werkflow `TenantAwareSignalService` scopes delivery per tenant. Proven in M4.13 audit. | **ENGINE-JUNIT** | Covered by `SignalTenantIsolationTest`. No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><rect x="12" y="12" width="12" height="12" rx="1"/><line x1="12" y1="16" x2="24" y2="16"/><line x1="12" y1="20" x2="24" y2="20"/></svg> | Pauses until a condition evaluates true | **PARTIAL** | bpmn-js authors conditional definitions. Proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><line x1="12" y1="18" x2="22" y2="18"/><polyline points="19,14 24,18 19,22"/></svg> | "Go-to" connector within a process diagram | **NOT SUPPORTED** | Flowable 7.2 has no `LinkEventDefinition`. The parser drops the element, leaving an `IntermediateCatchEvent` with empty `eventDefinitions`. `WerkflowLinkEventValidator` hard-rejects at deploy with error code `WERKFLOW_LINK_EVENT_UNSUPPORTED`. | **NOT-TESTABLE-CI** | Validator fires before Flowable's own guard. Use direct sequence flow or subprocess boundary instead. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polygon points="18,11 22,25 14,25"/></svg> | Catches an escalation from a subprocess | **PARTIAL** | bpmn-js authors escalation definitions. Proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polyline points="10,20 14,20 14,16 18,16 18,20 22,20 22,16 26,16 26,20"/></svg> | Triggers compensation within a compensation handler | **PARTIAL** | bpmn-js authors compensation definitions. Proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |

### 1.3 Intermediate Throwing Events

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/></svg> | Passes-through; used as a visual marker | **NOT SUPPORTED** | `WerkflowLinkEventValidator` detects `ThrowEvent` with empty `eventDefinitions` (which is how a none-throw appears after Flowable's parser) and hard-rejects it with `WERKFLOW_LINK_EVENT_UNSUPPORTED`. Use a sequence flow or annotation instead. | **NOT-TESTABLE-CI** | Rejection is conservative and intentional; a none-throw with zero defs is indistinguishable from a broken link-throw at the Flowable model level. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><rect x="11" y="13" width="14" height="10" rx="1" fill="currentColor"/></svg> | Sends a named message | **PARTIAL** | bpmn-js authors message definitions. Flowable engine dispatches the message internally. No Werkflow webhook-out bridge. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow-specific test for message-throw dispatch. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polygon points="18,10 25,26 11,26" fill="currentColor"/></svg> | Broadcasts a named signal | **FULL** | `signalRef` authored by stock bpmn-js. Tenant-scoped delivery confirmed. Werkflow-specific rule: always model signals in BPMN (`intermediateThrowEvent`); never dispatch from Java with bare `runtimeService.signalEventReceived()`. | **ENGINE-JUNIT** | Covered by `SignalTenantIsolationTest`. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><line x1="12" y1="18" x2="22" y2="18" stroke-dasharray="2,2"/><polyline points="19,14 24,18 19,22"/></svg> | "Go-to" source connector | **NOT SUPPORTED** | Same as link catch: parser drops the `linkEventDefinition`, leaving a `ThrowEvent` with empty `eventDefinitions`. `WerkflowLinkEventValidator` rejects with `WERKFLOW_LINK_EVENT_UNSUPPORTED`. | **NOT-TESTABLE-CI** | See link catch notes. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polygon points="18,11 22,25 14,25" fill="currentColor"/></svg> | Escalates to a parent process or boundary handler | **PARTIAL** | bpmn-js authors escalation definitions. Proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="12"/><polyline points="10,20 14,20 14,16 18,16 18,20 22,20 22,16 26,16 26,20" fill="currentColor" stroke="none"/></svg> | Triggers compensation rollback | **PARTIAL** | bpmn-js authors compensation definitions. Proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |

### 1.4 End Events

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> | Process path terminates normally | **FULL** | No extension; pure Flowable standard. Used in all 4 seeded examples. | **PLAYWRIGHT** | Covered across `03-start-process.spec.ts`, `26-workflow-leave-request.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><rect x="11" y="13" width="14" height="10" rx="1" fill="currentColor" stroke="none"/></svg> | Sends a message on process end | **PARTIAL** | bpmn-js can author message end; Flowable 7.2 supports it. No Werkflow-specific integration. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow test confirms message-end execution. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><polygon points="18,10 25,26 11,26" fill="currentColor" stroke="none"/></svg> | Broadcasts a signal on process end | **PARTIAL** | `signalRef` authored by stock bpmn-js. Flowable executes. Tenant-scoped rule applies. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><polyline points="14,12 18,18 16,21 20,21 18,25 22,18 20,18 24,12" fill="currentColor" stroke="none"/></svg> | Throws a BPMN error (caught by error boundary or error start) | **PARTIAL** | bpmn-js authors error end events; Flowable 7.2 propagates the error. No custom panel. | **ENGINE-JUNIT** | Proven compatible with Flowable. No Werkflow-specific Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><polygon points="18,11 22,25 14,25" fill="currentColor" stroke="none"/></svg> | Escalates to parent on end | **PARTIAL** | bpmn-js authors escalation end; proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><polyline points="10,20 14,20 14,16 18,16 18,20 22,20 22,16 26,16 26,20" fill="currentColor" stroke="none"/></svg> | Triggers compensation on process end | **PARTIAL** | bpmn-js authors compensation end; proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="9" fill="currentColor" stroke="none"/></svg> | Terminates all active tokens in the process instance | **PARTIAL** | Proven executing per M4.13 audit. `EndEvent` does NOT extend `ThrowEvent` in Flowable 7.2 (extends `Event` directly), so `WerkflowLinkEventValidator`'s throw-event check does not affect terminate ends. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3" stroke="currentColor"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3" stroke="currentColor"/></svg> | Cancels an active transaction subprocess | **PARTIAL** | Flowable 7.2 supports cancel end inside transaction subprocesses. NEEDS VERIFICATION: not confirmed in Werkflow tests. | **ENGINE-JUNIT** | Only valid inside a transaction subprocess. |

### 1.5 Boundary Events (Interrupting)

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="8"/><line x1="18" y1="10" x2="18" y2="18"/><line x1="18" y1="18" x2="23" y2="18"/></svg> | Cancels the activity and reroutes after timeout | **PARTIAL** | bpmn-js authors timer boundary; Flowable schedules the timeout. `flowable:formKey` and `triggerProcess` are available via `isFormCapableElement`. | **PLAYWRIGHT (short)** | Short duration substitution required. No existing Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><rect x="11" y="13" width="14" height="10" rx="1"/><polyline points="11,13 18,19 25,13"/></svg> | Cancels the activity when a message arrives | **PARTIAL** | bpmn-js authors; `flowable:formKey` + `triggerProcess` available. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow test. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polygon points="18,10 25,26 11,26"/></svg> | Cancels the activity when a named signal fires | **PARTIAL** | bpmn-js authors; tenant-scoped signal rules apply. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polyline points="14,12 18,18 16,21 20,21 18,25 22,18 20,18 24,12"/></svg> | Catches an error thrown by the activity | **PARTIAL** | bpmn-js authors; Flowable propagates BPMN errors upward. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow-specific test. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polygon points="18,11 22,25 14,25"/></svg> | Catches an escalation from a subprocess | **PARTIAL** | bpmn-js authors; proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><polyline points="10,20 14,20 14,16 18,16 18,20 22,20 22,16 26,16 26,20"/></svg> | Marks an activity's compensation handler | **PARTIAL** | bpmn-js authors; proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/><rect x="12" y="12" width="12" height="12" rx="1"/><line x1="12" y1="16" x2="24" y2="16"/><line x1="12" y1="20" x2="24" y2="20"/></svg> | Fires when a condition becomes true on the attached activity | **PARTIAL** | bpmn-js authors; proven executing per M4.13 audit. | **ENGINE-JUNIT** | No Playwright spec. |

### 1.6 Boundary Events (Non-Interrupting)

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><circle cx="18" cy="18" r="15"/><circle cx="18" cy="18" r="8" stroke-dasharray="none"/><line x1="18" y1="10" x2="18" y2="18" stroke-dasharray="none"/><line x1="18" y1="18" x2="23" y2="18" stroke-dasharray="none"/></svg> | Fires at timeout; activity continues | **PARTIAL** | bpmn-js authors; Flowable supports non-interrupting timer boundaries. | **PLAYWRIGHT (short)** | No existing spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><circle cx="18" cy="18" r="15"/><rect x="11" y="13" width="14" height="10" rx="1" stroke-dasharray="none"/><polyline points="11,13 18,19 25,13" stroke-dasharray="none"/></svg> | Fires when a message arrives; activity continues | **PARTIAL** | bpmn-js authors; Flowable supports. NEEDS VERIFICATION in Werkflow tests. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><circle cx="18" cy="18" r="15"/><polygon points="18,10 25,26 11,26" stroke-dasharray="none"/></svg> | Fires on signal; activity continues | **PARTIAL** | bpmn-js authors; tenant-scoped rules apply. NEEDS VERIFICATION in Werkflow tests. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><circle cx="18" cy="18" r="15"/><polygon points="18,11 22,25 14,25" stroke-dasharray="none"/></svg> | Fires on escalation; activity continues | **PARTIAL** | bpmn-js authors; Flowable supports. NEEDS VERIFICATION in Werkflow tests. | **ENGINE-JUNIT** | No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><circle cx="18" cy="18" r="15"/><rect x="12" y="12" width="12" height="12" rx="1" stroke-dasharray="none"/><line x1="12" y1="16" x2="24" y2="16" stroke-dasharray="none"/><line x1="12" y1="20" x2="24" y2="20" stroke-dasharray="none"/></svg> | Fires when condition is true; activity continues | **PARTIAL** | bpmn-js authors; Flowable supports. NEEDS VERIFICATION in Werkflow tests. | **ENGINE-JUNIT** | No Playwright spec. |

---

## 2. Activities

### 2.1 Tasks

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> | Human performs a work item | **FULL** | Properties panel: `flowable:candidateGroups` (dynamic group dropdown from API), `flowable:formKey` (form-js form select), `priority` and `dueDate` (expression combobox with DTDS variable autocomplete). Action type `HUMAN_APPROVAL` auto-morphs any task to `bpmn:UserTask`. ApprovalPanel rendered in portal for `HUMAN_APPROVAL` tasks. `GlobalTaskNotificationListener` fires email notifications on task create. | **PLAYWRIGHT** | `04-task-approval.spec.ts`, `05-requests.spec.ts`, `26-workflow-leave-request.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/></svg> | System executes automatically | **FULL** | Designer panel: action type `CONNECTOR_OPERATION` or `SET_VARIABLES`. No static delegate set — `ConnectorOperationSection` resolves transport (REST → `${externalApiCallDelegate}`, DB → `${databaseConnectorDelegate}`) when connector is chosen. `SET_VARIABLES` auto-assigns `${setVariablesDelegate}`. All EL expressions evaluated by `RestrictedExpressionManager`. | **PLAYWRIGHT** | `13-action-blocks.spec.ts`, `22-connector-setup.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> | Evaluates a DMN decision table | **FULL** | `flowable:type="dmn"` + `decisionTableReferenceKey` field extension. Designer renders "DMN Decision" group with decision key select and binding toggle (`same-deployment` / `latest`). Visual tint (indigo) distinguishes DMN tasks. Verified by `DmnDecisionTaskExecutionTest`. ADR-026. **NOTE: `bpmn:businessRuleTask` does NOT bind DMN in Flowable 7.2 — see BusinessRuleTask row.** | **PLAYWRIGHT** | `23-dmn-decisions.spec.ts`, `26-workflow-leave-request.spec.ts`, `25-workflow-event-ticket.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> | Sends a message or notification | **FULL** | `WerkflowSendTaskParseHandler` + `WerkflowSendTaskValidator` wire the send task at deploy. Action type `SEND_NOTIFICATION` auto-morphs to `bpmn:SendTask`, assigns `${notificationDelegate}`, seeds `channel=email` default. `NotificationDelegate` dispatches via `NotificationChannelFactory` (Email active; Slack/WhatsApp listed but disabled in designer). ADR-015. | **PLAYWRIGHT** | `13-action-blocks.spec.ts`. Email channel covered in `25-workflow-event-ticket.spec.ts` (notification service task pattern). |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1"/><polyline points="7,8 12,13 17,8" fill="none"/></svg> | Waits for a message to arrive | **DEPRECATED** | Properties panel renders a `DeprecationNoticeEntry` read-only alert when a receive task is selected: "ReceiveTask is deprecated in Werkflow. Replace with a Message Intermediate Catch Event and a webhook connector." Engine still executes it via Flowable standard behaviour. | **ENGINE-JUNIT** | No ADR yet. Treat as designer-discouraged. Use message intermediate catch + webhook connector instead. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><path d="M7,10 L10,8 L13,10 L13,16 L7,16 Z M10,8 L10,6"/></svg> | Human performs offline work with no system interaction | **DEPRECATED** | ADR-017: `MANUAL_STEP + confirmationRequired=true` variant deprecated. `WerkflowManualTaskValidator` hard-rejects any `bpmn:manualTask` carrying `confirmationRequired=true` with error code `WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED`. Plain manual tasks (no confirmation field) still deploy but are a pass-through in Flowable — no engine wait semantics. Designer retains the action type `MANUAL_STEP` in the palette for legacy compatibility. Prefer `HUMAN_APPROVAL` (user task) for any step requiring acknowledgement. | **ENGINE-JUNIT** | Deprecated by ADR-017. No Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="9" rx="1"/><line x1="9" y1="11" x2="15" y2="11"/><line x1="9" y1="14" x2="15" y2="14"/></svg> | Executes a business rule (Drools/KIE in Flowable 7.2) | **QUARANTINED** | `WerkflowBusinessRuleTaskValidator` hard-rejects every `bpmn:businessRuleTask` at deploy with error code `WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED`. **Reason:** Flowable 7.2 routes `businessRuleTask` to the legacy Drools/KIE rule engine, never to `DmnActivityBehavior`. Werkflow does not ship Drools. DMN is exclusively authored as `serviceTask flowable:type="dmn"`. Confirmed by ADR-026 and `WerkflowBusinessRuleTaskValidator`. | **NOT-TESTABLE-CI** | If you see a Camunda BPMN diagram using `businessRuleTask` for DMN, it must be reauthored as a `serviceTask` with `flowable:type="dmn"`. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="7" width="9" height="11" rx="1"/><path d="M9,7 Q11,4 13,7"/><line x1="9" y1="11" x2="14" y2="11"/><line x1="9" y1="14" x2="14" y2="14"/></svg> | Executes a script (Groovy in Flowable 7.2) | **QUARANTINED** | `WerkflowScriptTaskQuarantineValidator` hard-rejects every `bpmn:scriptTask` at deploy with error code `WERKFLOW_SCRIPT_TASK_QUARANTINED`. **Reason:** Groovy script tasks pose an unmitigated RCE risk against the engine JVM (ADR-016 Phase 1). ADR-016 Phase 2 (sandboxed Groovy via `SecureASTCustomizer`) is deferred post-MVP. Designer properties panel shows a "Script" panel (format + body fields) to preserve visibility but engine will block deployment. | **NOT-TESTABLE-CI** | No bypass flag exists. Phase 2 sandboxing must be completed before script tasks can be allowed. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="3"><rect x="3" y="3" width="54" height="30" rx="6"/><polyline points="22,14 28,18 22,22" stroke-width="2"/></svg> | Long-running or externally-polled work item | **PARTIAL** | Flowable 7.2 supports external tasks natively (worker registers, polls, locks, completes). Werkflow provides no dedicated portal UI for worker management. ADR-019 through ADR-022 documented the external worker integration pattern (M4.11 P3 audit). `ConnectorWebhookDelegate` + `ConnectorDelegateBase` cover the inbound-webhook side of this pattern. | **ENGINE-JUNIT** | No Playwright spec for external task polling workflow. Webhook trigger covered by `22-connector-setup.spec.ts`. |

### 2.2 Subprocess Types

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="25" y="27" width="10" height="6" rx="1"/><line x1="28" y1="27" x2="28" y2="33"/><line x1="25" y1="30" x2="35" y2="30"/></svg> | Groups activities into a reusable collapsed or expanded scope | **PARTIAL** | bpmn-js supports authoring embedded subprocesses. Flowable executes them. No Werkflow-specific properties panel for subprocess-level configuration. `WerkflowLinkEventValidator` scans flow elements recursively including those inside subprocesses (`findFlowElementsOfType(FlowElement.class, true)`). | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Playwright spec confirms subprocess deploy + execution. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="12" cy="12" r="6" stroke-dasharray="none"/><rect x="25" y="27" width="10" height="6" rx="1" stroke-dasharray="none"/><line x1="28" y1="27" x2="28" y2="33" stroke-dasharray="none"/><line x1="25" y1="30" x2="35" y2="30" stroke-dasharray="none"/></svg> | A subprocess triggered by an event (start event inside a subprocess) | **PARTIAL** | bpmn-js authors event subprocesses. Flowable 7.2 supports them. Error-start-in-event-subprocess pattern is valid. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow-specific test. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="5" y="5" width="50" height="26" rx="5"/><rect x="25" y="27" width="10" height="6" rx="1"/><line x1="28" y1="27" x2="28" y2="33"/><line x1="25" y1="30" x2="35" y2="30"/></svg> | Groups activities under ACID-like compensation semantics | **PARTIAL** | bpmn-js authors transactions. Flowable 7.2 supports transaction subprocesses with compensation. NEEDS VERIFICATION in Werkflow deploy tests. | **ENGINE-JUNIT** | Cancel-end event is only valid inside a transaction subprocess. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><text x="22" y="30" font-size="14" fill="currentColor" stroke="none" font-family="serif">~</text></svg> | Unordered set of activities; participants choose which to execute | **NEEDS VERIFICATION** | bpmn-js can author ad-hoc subprocesses. Flowable 7.2 support is partial and undocumented for Werkflow. NEEDS VERIFICATION: confirm whether Flowable 7.2 supports ad-hoc subprocess execution. | **NOT-TESTABLE-CI** | Do not use in production processes until verified. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="4"><rect x="3" y="3" width="54" height="30" rx="6"/></svg> | Invokes a separately deployed process as a subprocess | **FULL** | Designer action type `CALL_SUBPROCESS` auto-morphs to `bpmn:CallActivity`. Properties panel: `calledElement` select (populated from deployed process definitions), `flowable:In` / `flowable:Out` variable mapping rows. Native `bpmn:CallActivity`; no delegate required. `CALL_SUBPROCESS` entries in `buildActionBlockEntries`. | **PLAYWRIGHT** | `13-action-blocks.spec.ts` (action block smoke). NEEDS VERIFICATION: no spec confirms cross-process variable passing via in/out mappings end-to-end. |

### 2.3 Multi-Instance Markers

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><line x1="25" y1="27" x2="25" y2="33"/><line x1="30" y1="27" x2="30" y2="33"/><line x1="35" y1="27" x2="35" y2="33"/></svg> | Activity executes N times in sequence, once per element | **PARTIAL** | bpmn-js authors multi-instance markers (sequential: `isSequential=true`). Flowable 7.2 supports sequential multi-instance on tasks and subprocesses. No Werkflow-specific panel entries for multi-instance configuration. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow example or Playwright spec uses multi-instance. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><line x1="25" y1="27" x2="35" y2="27"/><line x1="25" y1="30" x2="35" y2="30"/><line x1="25" y1="33" x2="35" y2="33"/></svg> | Activity executes N times concurrently | **PARTIAL** | bpmn-js authors parallel multi-instance markers (`isSequential=false`). Flowable 7.2 supports parallel multi-instance. No Werkflow-specific panel entries. | **ENGINE-JUNIT** | NEEDS VERIFICATION: same as sequential above. |

---

## 3. Gateways

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> | Routes to exactly one outgoing path based on conditions | **FULL** | Stock bpmn-js; Flowable executes. EL conditions evaluated by `RestrictedExpressionManager` (ADR-013). Conditions authored as JUEL expressions (e.g., `${decision == 'approved'}`). Used in all 4 seeded example processes. | **PLAYWRIGHT** | `03-start-process.spec.ts`, `23-dmn-decisions.spec.ts`, `25-workflow-event-ticket.spec.ts`, `26-workflow-leave-request.spec.ts` |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="18" y1="11" x2="18" y2="25" stroke-width="3"/><line x1="11" y1="18" x2="25" y2="18" stroke-width="3"/></svg> | Splits to all outgoing paths; joins when all incoming paths complete | **PARTIAL** | bpmn-js authors; Flowable executes AND-split and AND-join. No Werkflow example uses a parallel gateway today. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Playwright spec. Flowable support is confirmed at engine level. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><circle cx="18" cy="18" r="6" stroke-width="3"/></svg> | Routes to one or more outgoing paths where conditions are true | **PARTIAL** | bpmn-js authors; Flowable executes OR-split and OR-join. No Werkflow example uses an inclusive gateway today. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><polygon points="18,11 21,16 27,16 22,20 24,26 18,22 12,26 14,20 9,16 15,16"/></svg> | Routes based on which event fires first | **PARTIAL** | bpmn-js authors event-based gateways. Flowable 7.2 supports them. No Werkflow example uses one. | **ENGINE-JUNIT** | NEEDS VERIFICATION: no Werkflow-specific test. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="18" y1="11" x2="18" y2="25" stroke-width="2"/><line x1="11" y1="18" x2="25" y2="18" stroke-width="2"/><line x1="13" y1="13" x2="23" y2="23" stroke-width="2"/><line x1="23" y1="13" x2="13" y2="23" stroke-width="2"/></svg> | Custom merge/split conditions via expressions | **NOT SUPPORTED** | bpmn-js can render complex gateways. Flowable 7.2 support is very limited and undocumented for Werkflow. NEEDS VERIFICATION before any client use. | **NOT-TESTABLE-CI** | Do not use; use exclusive or inclusive gateway instead. |

---

## 4. Sequence Flow and Conditional Flow

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="16" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="50" y2="6"/><polyline points="46,3 54,6 46,9"/></svg> | Connects flow elements in order | **FULL** | Stock bpmn-js authoring; Flowable execution. No Werkflow extension. | **PLAYWRIGHT** | Covered in every Playwright spec. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="16" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="50" y2="6"/><polyline points="46,3 54,6 46,9"/><polygon points="3,3 9,6 3,9" fill="currentColor" stroke="none"/></svg> | Sequence flow with a JUEL expression condition | **FULL** | EL expressions authored via bpmn-js condition expression editor. All expressions evaluated by `RestrictedExpressionManager` (ADR-013); dangerous classes/methods are blocked by `SecurityELResolver`. `ExpressionAuditLogger` logs every evaluation. Outgoing conditions on exclusive/inclusive gateways are the canonical pattern. | **PLAYWRIGHT** | `03-start-process.spec.ts`, `23-dmn-decisions.spec.ts`. ExpressionBuilder unit-tested in `ExpressionBuilder.test.tsx`. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="16" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="50" y2="6"/><polyline points="46,3 54,6 46,9"/><line x1="3" y1="1" x2="9" y2="11" stroke-width="2"/></svg> | Taken when no other condition is satisfied | **PARTIAL** | bpmn-js authors default flows (diamond marker on gateway). Flowable executes. NEEDS VERIFICATION: no Werkflow-specific test that exercises default flow path. | **ENGINE-JUNIT** | Use as fallback branch on exclusive gateways to prevent stuck tokens. |

---

## 5. Data Objects, Data Stores, and Data Associations

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="20" height="24" viewBox="0 0 30 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M3,3 L20,3 L27,10 L27,33 L3,33 Z"/><polyline points="20,3 20,10 27,10"/></svg> | Models data produced or consumed by an activity | **NOT SUPPORTED** | bpmn-js can render data objects as visual annotations. Flowable 7.2 does not wire data objects to process variable behaviour. No Werkflow designer panel for data objects. Use process variables (set via `SET_VARIABLES` action type or form submissions) instead. | **NOT-TESTABLE-CI** | Visual only in bpmn-js; no engine behaviour. |
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="18" cy="8" rx="13" ry="5"/><line x1="5" y1="8" x2="5" y2="28"/><line x1="31" y1="8" x2="31" y2="28"/><ellipse cx="18" cy="28" rx="13" ry="5"/></svg> | Models persistent data shared across processes | **NOT SUPPORTED** | Same as data objects — bpmn-js visual only, no Flowable execution binding. | **NOT-TESTABLE-CI** | Use connector service tasks with database credentials for actual DB access. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="16" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4,2"><line x1="3" y1="6" x2="54" y2="6"/></svg> | Connects data objects/stores to activities | **NOT SUPPORTED** | No engine binding. | **NOT-TESTABLE-CI** | Not applicable given data objects are not supported. |

---

## 6. Pools, Lanes, and Participants

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="2"/><line x1="13" y1="3" x2="13" y2="33"/></svg> | Represents an organisation or system boundary | **NOT SUPPORTED** | bpmn-js supports pools as visual containers. Flowable 7.2 executes only the process inside a pool (the collaboration construct has no engine semantics itself). Werkflow's designer does not add specific pool configuration. Cross-pool message flows are not wired to any Werkflow connector. | **NOT-TESTABLE-CI** | Use pools for visual communication diagrams only. For actual cross-system integration use connector service tasks. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="2"/><line x1="13" y1="3" x2="13" y2="33"/><line x1="13" y1="18" x2="57" y2="18"/></svg> | Swimlane subdivision of a pool; represents a role or group | **PARTIAL** | bpmn-js renders lanes visually. Flowable 7.2 passes lane information through but does not enforce role assignment on tasks within a lane — that is done via `flowable:candidateGroups` on individual tasks. No Werkflow-specific lane-to-group mapping is automated. | **NOT-TESTABLE-CI** | Lane assignment in the designer is documentation-only; always set `candidateGroups` on individual user tasks. |

---

## 7. Artifacts

| Symbol | Standard BPMN Meaning | Werkflow Support | Custom Capability / Extension | E2E Testability | Notes |
|---|---|---|---|---|---|
| <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M26,5 L10,5 L10,31 L26,31"/></svg> | Free-text comment attached to a flow element | **FULL** | bpmn-js standard; no engine behaviour. Used freely in seeded example BPMNs for inline documentation (e.g., Flowable 7.2 DMN routing notes). | **NOT-TESTABLE-CI** | Visual only; does not affect execution. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="6,3"><rect x="3" y="3" width="54" height="30" rx="6"/></svg> | Visual grouping of elements without affecting flow | **PARTIAL** | bpmn-js authors groups; Flowable passes through; `BpmnGroupValidator` validates group configuration in Werkflow. | **NOT-TESTABLE-CI** | Visual grouping only. |
| <svg xmlns="http://www.w3.org/2000/svg" width="36" height="16" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="3,2"><line x1="3" y1="6" x2="54" y2="6"/></svg> | Connects an artifact to a flow element | **PARTIAL** | bpmn-js authors associations (solid or dashed lines). Used to link text annotations to activities. No engine behaviour. | **NOT-TESTABLE-CI** | Visual only. |

---

## 8. Custom Werkflow Capabilities

This section documents Werkflow-specific extensions that go beyond stock BPMN 2.0.

### 8.1 DMN-as-ServiceTask Binding (ADR-026)

**What it is:** The canonical way to evaluate a DMN decision table in Werkflow.

**How it works:**
- Author a `serviceTask` with `flowable:type="dmn"` and a `flowable:field` named `decisionTableReferenceKey` whose value is the DMN decision key.
- `DmnDecisionService` resolves the decision at runtime.
- Outputs become process variables directly.
- Binding mode: `sameDeployment` (default, pinned to bundle) or `latest` (always newest deployed version). Controlled via a second `flowable:field` named `sameDeployment` (`"false"` = latest).

**Designer support:** Full. The "DMN Decision" group appears when a `ServiceTask` is selected; decision key select + binding toggle. DMN tasks are visually tinted indigo (`fill: #e8eaf6`, `stroke: #283593`).

**What NOT to use:** `bpmn:businessRuleTask` — hard-rejected by `WerkflowBusinessRuleTaskValidator` (ADR-026). Camunda diagrams using `businessRuleTask` for DMN must be reauthored.

### 8.2 Connector Tasks

**What it is:** Service tasks that call external systems via Werkflow's connector framework.

| Delegate | Transport | Designer Action Type |
|---|---|---|
| `${externalApiCallDelegate}` (`RestConnectorDelegate`) | HTTP REST | `CONNECTOR_OPERATION` |
| `${databaseConnectorDelegate}` (`DatabaseConnectorDelegate`) | JDBC / SQL | `CONNECTOR_OPERATION` |
| `${webhookConnectorDelegate}` (`ConnectorWebhookDelegate`) | Inbound webhook (HMAC-verified) | No dedicated action type — used on receive events |

**Credential resolution:** All connector credentials are resolved server-side from OpenBao (HashiCorp Vault-compatible). The engine fetches the `credentialRef` via `ConnectorCredentialBindingClient`; no secret value is ever stored in BPMN XML. ADR-024 (credential resolution model).

**Designer panel:** `ConnectorOperationSection` in the React sidebar. Connector type (REST/DB) is selected, which sets the appropriate delegate expression. Output variable name is configured per connector instance.

### 8.3 Notification Delegate

**What it is:** `NotificationDelegate` (`${notificationDelegate}`) sends notifications via the `NotificationChannelFactory`.

**How to use:** Author a `bpmn:SendTask` with action type `SEND_NOTIFICATION`. Required `flowable:field` extensions:
- `channel`: `email` (active), `slack` / `whatsapp` (disabled in designer, marked coming-soon)
- `templateKey`: key of a notification template managed via `/admin/email-templates`
- `recipient`: EL expression (e.g., `${email}`) or a literal address

`GlobalTaskNotificationListener` additionally fires automatic email notifications on task creation (independent of send tasks).

### 8.4 RestrictedExpressionManager (ADR-013)

**What it is:** `RestrictedExpressionManager` replaces Flowable's default JUEL expression manager. Wired at `FlowableConfig:80`.

**What it provides:**
- `SecurityELResolver`: blocks access to dangerous Java classes, reflection, and system operations via a blocklist. All expressions that reference blocked classes fail with `WerkflowExpressionEvaluationException`.
- `ExpressionAuditLogger`: structured logging of every EL expression evaluation (element ID, expression text, outcome).
- `FunctionRegistry` / `SafeFunctionDelegate`: curated safe utility functions (`DateUtilFunctions`, `MathUtilFunctions`, `StringUtilFunctions`) available in EL context.
- `ExpressionLimitsConfig`: configurable max expression length and max nesting depth.

**Scope:** Applies to all EL expressions in the engine — sequence flow conditions, task assignments, service task expressions, and listener expressions.

### 8.5 Deploy-Time Validators

| Validator Class | Error Code | What it Rejects |
|---|---|---|
| `WerkflowScriptTaskQuarantineValidator` | `WERKFLOW_SCRIPT_TASK_QUARANTINED` | Any `bpmn:scriptTask` (ADR-016 Phase 1 RCE quarantine) |
| `WerkflowBusinessRuleTaskValidator` | `WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED` | Any `bpmn:businessRuleTask` (dead config in Flowable 7.2; routes to Drools not DMN; ADR-026) |
| `WerkflowLinkEventValidator` | `WERKFLOW_LINK_EVENT_UNSUPPORTED` | Link catch/throw events and none-intermediate-throws (Flowable parser drops `linkEventDefinition`; F-EV-8) |
| `WerkflowManualTaskValidator` | `WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED` | `bpmn:manualTask` with `confirmationRequired=true` field (ADR-017; pass-through semantics would silently skip confirmation) |
| `WerkflowDeadExtensionAttrValidator` | `WERKFLOW_DEAD_EXTENSION_ATTR` | Four dead `flowable:*` extension attributes: `signalName`, `correlationKey`, `webhookConnector`, `correlationExpression` (M4.13 F-EV-2 dead-attr class; ADR-009) |
| `BpmnFormKeyValidator` | (internal) | Validates form key format and existence against deployed form schemas |
| `BpmnGroupValidator` | (internal) | Validates candidate group references |

### 8.6 Tenant-Scoped Form Resolution

`ProcessExampleDeployer` seeds BPMN + form + DMN atomically as one logical unit per tenant on startup. `getStartForm` is tenant-scoped (session 40 fix) — a form key is resolved only within the requesting tenant's deployment, preventing cross-tenant form bleed.

### 8.7 ProcessExampleDeployer

Seeds the 4 bundled example processes on engine startup:
- `capex-approval-process` — CapEx multi-tier approval (DMN + exclusive gateways + notification)
- `leave-request` — Leave approval (DMN-routed, auto or manual approval)
- `procurement-approval-process` — Procurement (multi-step user tasks + signal definition)
- `finance-approval-process` — Finance review (basic single-approver exclusive gateway)

Orphaned drafts from previous deployments are auto-pruned. Each seed is idempotent.

---

## 9. E2E Coverage Matrix

Maps every symbol category to existing Playwright specs or flags the gap.

| Symbol Category | E2E Testability | Existing Playwright Spec | Gap / Proposed Test |
|---|---|---|---|
| None start event | PLAYWRIGHT | `03-start-process.spec.ts`, `26-workflow-leave-request.spec.ts` | — |
| Message start | ENGINE-JUNIT | None | Propose: `engine/MessageStartEventIT.java` |
| Timer start | ENGINE-JUNIT | None | Propose: `engine/TimerStartEventIT.java` (PT2S) |
| Signal start | ENGINE-JUNIT | Engine: `SignalTenantIsolationTest` | No Playwright spec |
| Conditional start | ENGINE-JUNIT | None | Propose: `engine/ConditionalStartEventIT.java` |
| Error start (event subprocess) | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Message catch (intermediate) | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Timer catch (intermediate) | PLAYWRIGHT (short) | None | Propose: short-duration timer in `e2e/tests/business/` |
| Signal catch (intermediate) | ENGINE-JUNIT | Engine: `SignalTenantIsolationTest` | No Playwright spec |
| Conditional catch | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Link catch | NOT-TESTABLE-CI | Validator test in `WerkflowLinkEventValidatorTest` | N/A — blocked at deploy |
| Escalation catch | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Compensation catch | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| None throw (blocked) | NOT-TESTABLE-CI | `WerkflowLinkEventValidatorTest` | N/A — blocked at deploy |
| Message throw | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Signal throw | ENGINE-JUNIT | Engine: `SignalTenantIsolationTest` | No Playwright spec |
| Escalation throw | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Compensation throw | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| None end | PLAYWRIGHT | `03-start-process.spec.ts`, `26-workflow-leave-request.spec.ts` | — |
| Message/Signal/Error/Escalation/Compensation end | ENGINE-JUNIT | None | Propose JUnit tests per type |
| Terminate end | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Timer boundary (interrupting) | PLAYWRIGHT (short) | None | Propose: `e2e/tests/business/28-boundary-timer.spec.ts` |
| Error boundary | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Other boundary events | ENGINE-JUNIT | None | Propose JUnit tests per type |
| User task | PLAYWRIGHT | `04-task-approval.spec.ts`, `26-workflow-leave-request.spec.ts` | — |
| Service task (plain / connector) | PLAYWRIGHT | `13-action-blocks.spec.ts`, `22-connector-setup.spec.ts` | — |
| Service task (DMN) | PLAYWRIGHT | `23-dmn-decisions.spec.ts`, `25-workflow-event-ticket.spec.ts` | — |
| Send task | PLAYWRIGHT | `13-action-blocks.spec.ts` (smoke) | Full email round-trip: `25-workflow-event-ticket.spec.ts` |
| Receive task | ENGINE-JUNIT | None | Deprecated; low priority |
| Manual task | ENGINE-JUNIT | None | Deprecated; low priority |
| Business rule task | NOT-TESTABLE-CI | `WerkflowBusinessRuleTaskValidatorTest` | N/A — blocked at deploy |
| Script task | NOT-TESTABLE-CI | `WerkflowScriptTaskQuarantineValidatorTest` | N/A — blocked at deploy |
| External task | ENGINE-JUNIT | None | Propose: external worker polling integration test |
| Embedded subprocess | ENGINE-JUNIT | None | NEEDS VERIFICATION; then propose Playwright spec |
| Event subprocess | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Transaction subprocess | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Ad-hoc subprocess | NOT-TESTABLE-CI | None | NEEDS VERIFICATION before enabling |
| Call activity | PLAYWRIGHT | `13-action-blocks.spec.ts` (smoke) | Propose: full cross-process test with variable mapping |
| Sequential multi-instance | ENGINE-JUNIT | None | NEEDS VERIFICATION; propose JUnit test |
| Parallel multi-instance | ENGINE-JUNIT | None | NEEDS VERIFICATION; propose JUnit test |
| Exclusive gateway | PLAYWRIGHT | `03-start-process.spec.ts`, `23-dmn-decisions.spec.ts`, `26-workflow-leave-request.spec.ts` | — |
| Parallel gateway | ENGINE-JUNIT | None | Propose: `engine/ParallelGatewayIT.java` |
| Inclusive gateway | ENGINE-JUNIT | None | Propose: `e2e/tests/business/28-inclusive-gateway.spec.ts` |
| Event-based gateway | ENGINE-JUNIT | None | NEEDS VERIFICATION first |
| Complex gateway | NOT-TESTABLE-CI | None | Do not use |
| Sequence flow / Conditional flow | PLAYWRIGHT | All business specs | — |
| Default flow | ENGINE-JUNIT | None | NEEDS VERIFICATION; propose JUnit test |
| Data objects / stores | NOT-TESTABLE-CI | None | Not applicable |
| Pools / lanes | NOT-TESTABLE-CI | None | Visual only |
| Text annotation / Group / Association | NOT-TESTABLE-CI | None | Visual only |

---

*Document version: MVP release draft. Review against live codebase before publishing to clients. Items marked NEEDS VERIFICATION require a targeted grep or JUnit probe before marking FULL or PARTIAL.*
