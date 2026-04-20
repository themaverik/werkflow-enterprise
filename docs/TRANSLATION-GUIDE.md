# Translation Guide

Werkflow uses [next-intl](https://next-intl-docs.vercel.app/) for internationalization. The Community Edition ships with English only. This guide explains how to add a new language.

---

## How It Works

All user-facing strings live in `frontends/portal/messages/en.json`, organized by top-level namespace keys. The locale is resolved server-side from the `Accept-Language` request header and defaults to `en`.

- **Server components** use `getTranslations('namespace')` from `next-intl/server`
- **Client components** use `useTranslations('namespace')` from `next-intl`
- The root layout wraps the app in `NextIntlClientProvider` so all client components receive translations

---

## Namespace Reference

| Namespace | Used In |
|-----------|---------|
| `nav` | Sidebar, header, user menu |
| `dashboard` | Dashboard page |
| `tasks` | Tasks page and task components |
| `requests` | My Requests page |
| `processes` | Process Designer page |
| `forms` | Form Designer page |
| `services` | Services page |
| `admin.connectors` | Admin → Connectors |
| `admin.custody` | Admin → Custody Mappings |
| `admin.departments` | Admin → Departments |
| `admin.doa` | Admin → Authority Levels |
| `auth` | Login page |
| `bpmn` | BPMN Designer, Expression Builder, Service Task panel |
| `formBuilder` | Form Builder, Form Viewer |
| `common` | Shared UI (ConfirmDialog, ErrorDisplay, ComingSoonPage) |

### Key Naming Convention

```
{namespace}.{camelCase}
```

Examples: `nav.myTasks`, `admin.departments.addDepartment`, `common.confirmDialog.confirm`

ICU message format is used for interpolation:

```json
"showingCount": "Showing {start} to {end} of {total} tasks"
```

---

## Adding a New Language

### 1. Copy the English message file

```bash
cp frontends/portal/messages/en.json frontends/portal/messages/fr.json
```

### 2. Translate the values

Edit `frontends/portal/messages/fr.json` and translate every value. Keep all keys unchanged — only translate the string values.

```json
{
  "nav": {
    "myTasks": "Mes tâches",
    ...
  }
}
```

### 3. Register the locale in `i18n/request.ts`

```ts
// frontends/portal/i18n/request.ts
import { getRequestConfig } from 'next-intl/server'
import { headers } from 'next/headers'

export default getRequestConfig(async () => {
  // Detect locale from Accept-Language header, fall back to 'en'
  const acceptLanguage = (await headers()).get('accept-language') ?? ''
  const preferred = acceptLanguage.split(',')[0].split('-')[0].trim()
  const supportedLocales = ['en', 'fr']
  const locale = supportedLocales.includes(preferred) ? preferred : 'en'

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
  }
})
```

### 4. Enable the locale in the language switcher

Find the `LanguageSwitcher` component in `components/layout/header.tsx` and replace the stub with the full dropdown implementation. The `TODO(i18n)` comment in that file marks the location.

### 5. Test your translation

```bash
cd frontends/portal
npm run dev
```

Open the portal in a browser. Set your `Accept-Language` header to `fr` (browser language settings or a browser extension) and verify all strings appear in French.

To check for missing keys:

```bash
# List any keys present in en.json but missing in fr.json
node -e "
const en = require('./messages/en.json');
const fr = require('./messages/fr.json');
const diff = (a, b, prefix='') => {
  for (const k of Object.keys(a)) {
    const path = prefix ? prefix+'.'+k : k;
    if (typeof a[k] === 'object') diff(a[k], b[k]||{}, path);
    else if (!(k in (b||{}))) console.log('MISSING:', path);
  }
};
diff(en, fr);
"
```

### 6. Submit a pull request

Open a PR against the `main` branch of the werkflow-public repository. Title: `i18n: add French (fr) translations`. Include the new `messages/fr.json` file and any `i18n/request.ts` changes.

---

## ICU Message Format

next-intl uses the [ICU message format](https://unicode-org.github.io/icu/userguide/format_parse/messages/). Key patterns:

| Pattern | Example key | Example usage |
|---------|-------------|---------------|
| Plain string | `"title": "Dashboard"` | `{t('title')}` |
| Interpolation | `"showingCount": "Showing {start}–{end} of {total}"` | `{t('showingCount', { start, end, total })}` |
| Plural | `"items": "{count, plural, one {# item} other {# items}}"` | `{t('items', { count })}` |

---

## Running a Build Check

After editing translations, verify the build compiles cleanly:

```bash
cd frontends/portal
npm run build
```

TypeScript will surface any missing or incorrectly typed translation key accesses.
