# Connector Guide

Connectors let you call external REST APIs from BPMN service tasks without hardcoding URLs. The engine resolves the base URL at runtime using the tenant's registered service endpoint.

---

## How Connectors Work

1. You register a connector (name + base URL) in the Admin UI or via the API
2. In a BPMN service task, you reference the connector by key and supply a path
3. At runtime, the engine looks up the base URL for that connector and constructs the full request URL

This means the same BPMN definition works across environments (dev/staging/prod) because the URL lives in configuration, not in the BPMN XML.

---

## Step 1 — Register a Connector

### Via the Admin UI

1. Log in to the Portal with an Admin account
2. Go to **Admin** → **Connectors**
3. Click **New Connector**
4. Fill in:
   - **Name**: A display label (e.g. `Webhook Test`)
   - **Connector Key**: A short identifier used in BPMN (e.g. `webhook-test`)
   - **Base URL**: The service root URL (e.g. `https://httpbin.org`)
5. Click **Save**

### Via the API

```bash
POST /werkflow/api/services/endpoints
Authorization: Bearer <token>
Content-Type: application/json

{
  "connectorKey": "webhook-test",
  "displayName": "Webhook Test",
  "baseUrl": "https://httpbin.org",
  "environment": "dev"
}
```

---

## Step 2 — Use the Connector in BPMN

In the BPMN designer, add a **Service Task** to your process. Open the **Properties Panel** and select the **Action Blocks** tab.

Set **Action Type** to `EXTERNAL_API_CALL` and configure:

| Field | Value | Description |
|---|---|---|
| `connector` | `webhook-test` | Registered connector key |
| `path` | `/post` | Path appended to the base URL |
| `method` | `POST` | HTTP method |
| `body` | `{"key": "${myVar}"}` | Request body (supports EL expressions) |
| `responseVariable` | `apiResult` | Process variable to store the response |
| `extractFields` | `status:$.json.key` | JSONPath extractions from the response |
| `onError` | `FAIL` | What to do on error: `FAIL`, `CONTINUE`, or `THROW_BPMN_ERROR` |

### BPMN XML example

```xml
<serviceTask id="callWebhook" name="Call Webhook"
             flowable:actionType="EXTERNAL_API_CALL"
             flowable:delegateExpression="${externalApiCallDelegate}">
  <extensionElements>
    <flowable:field name="connector">
      <flowable:string>webhook-test</flowable:string>
    </flowable:field>
    <flowable:field name="path">
      <flowable:string>/post</flowable:string>
    </flowable:field>
    <flowable:field name="method">
      <flowable:string>POST</flowable:string>
    </flowable:field>
    <flowable:field name="body">
      <flowable:string>{"requestId": "${requestId}", "amount": ${amount}}</flowable:string>
    </flowable:field>
    <flowable:field name="responseVariable">
      <flowable:string>webhookResult</flowable:string>
    </flowable:field>
    <flowable:field name="extractFields">
      <flowable:string>echoedId:$.json.requestId</flowable:string>
    </flowable:field>
    <flowable:field name="onError">
      <flowable:string>FAIL</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

---

## Step 3 — Test the Connector

Deploy the process and start an instance. Check the process variables after the service task completes — `webhookResult` will contain the raw API response and `echoedId` will contain the extracted value.

For quick testing, use `https://httpbin.org/post` which echoes the request body back as JSON.

---

## EL Expressions in Paths and Bodies

Both `path` and `body` fields support Flowable EL expressions. Process variables are accessible by name:

```
path:  /api/orders/${orderId}/approve
body:  {"approvedBy": "${initiator}", "amount": ${requestAmount}}
```

---

## Secret References

To pass an API key without storing it in the BPMN, use `secretRef`:

1. Register the secret in `config/env/.env.engine`:
   ```
   WERKFLOW_SECRETS_ALLOWED_KEYS=my-api-key
   werkflow.secrets.my-api-key=Bearer sk-xxxxx
   ```
2. Reference it in the BPMN:
   ```xml
   <flowable:field name="secretRef">
     <flowable:string>my-api-key</flowable:string>
   </flowable:field>
   ```

The secret value is injected as the `Authorization` header at runtime and never stored in process variables.

---

## SSRF Protection

All connector URLs are validated against the SSRF guard before execution. Requests to private IP ranges (10.x, 172.16–31.x, 192.168.x, 127.x, localhost) are blocked by default. This cannot be disabled.

---

## Related Documentation

- [Deployment Configuration](Deployment-Configuration-Guide.md)
- [BPMN Quick Reference](BPMN-Quick-Reference-Guide.md)
