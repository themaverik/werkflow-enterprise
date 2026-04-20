# Werkflow Keycloak Themes

Custom Keycloak themes with Werkflow branding for the login pages.

## Theme Structure

```
themes/
└── werkflow/
    └── login/
        ├── theme.properties
        ├── login.ftl
        └── css/
            └── login.css
```

## Configuration

### Docker Compose (Automatic)

The theme is automatically mounted and configured via `docker-compose.yml`:

```yaml
volumes:
  - ../keycloak/themes:/opt/keycloak/themes

environment:
  KC_SPI_THEME_DEFAULT: werkflow
```

### Manual Configuration (Admin UI)

If you need to change the theme after startup:

1. Go to Keycloak Admin Console: http://localhost:8090/admin
2. Select **Realm: werkflow**
3. Go to **Realm Settings** → **Themes** tab
4. Change **Login Theme** to `werkflow`
5. Click **Save**

## Customization

### Logo/Branding

The Werkflow logo is rendered in `login.ftl` line 7:
```html
<div class="login-pf-header-text">
    Werkflow
</div>
```

To use a custom image logo, modify the HTML and CSS:

```html
<img src="path/to/logo.svg" alt="Werkflow" class="werkflow-logo" />
```

And add CSS:
```css
.werkflow-logo {
  height: 50px;
  width: auto;
}
```

### Colors

Primary theme colors in `css/login.css`:
- Primary gradient: `#667eea` → `#764ba2`
- Text: `#333`
- Borders: `#e0e0e0`

Edit these hex codes to match your brand colors.

### Fonts

Font stack in `css/login.css`:
```css
font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
```

## Testing

1. Clear browser cookies to force re-login
2. Navigate to http://localhost:4000
3. Keycloak login page should show Werkflow branding

## Files

- **theme.properties** — Theme metadata and imports
- **login.ftl** — Keycloak login page template (Freemarker)
- **css/login.css** — Custom styling for login page
