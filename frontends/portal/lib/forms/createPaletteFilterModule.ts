/**
 * Palette filter utilities for form-js-editor v1.18.
 *
 * History:
 *  - v1 used `formFields.deregister()` — corrupted the type registry,
 *    leaving `_formFieldRegistry` empty on import and crashing drag/drop.
 *  - v2 used an `additionalModules` DI service injecting `'config'` then
 *    accessing `config?.renderer?.container`.  The correct DI token is
 *    `'config.renderer'` (resolves to `{ container, compact }`), not
 *    `'config'`.  The bad token caused silent DI failure during editor
 *    bootstrapping, again leaving the registry empty and crashing
 *    `Dragging.getTargetIndex` ("Cannot read properties of undefined
 *    (reading 'components')") on every palette drag.
 *  - v3 (current): no additionalModules at all.  The caller injects the
 *    `<style>` block directly into the container element after
 *    `importSchema` resolves, where `containerRef.current` is already
 *    available.  Zero DI risk; editor internals are never touched.
 *
 * Palette tiles render as:
 *   <button class="fjs-palette-field fjs-drag-copy fjs-no-drop"
 *           data-field-type="textfield" …>
 */

const STYLE_ID = 'werkflow-palette-filter'

/**
 * Builds a `<style>` block that hides every palette tile whose
 * `data-field-type` is NOT in `allowedTypes`.
 *
 * A second rule hides the group container when every one of its tiles is
 * hidden — achieved via `:not(:has(...visible tile...))`.
 */
function buildHideCss(allowedTypes: string[]): string {
  if (allowedTypes.length === 0) return ''

  // Chain :not() clauses — matches any tile NOT in the allowlist.
  const hiddenTileSelector = allowedTypes
    .map((t) => `:not([data-field-type="${t}"])`)
    .join('')

  // Matches a tile that IS in the allowlist (for the :has() group check).
  // Single-level :has() — nested :has(.fjs-palette-fields :has(...)) was
  // unreliable across browsers; a flat descendant query works everywhere
  // that supports :has() (Chrome 105+, Firefox 121+, Safari 15.4+).
  const visibleTileSelector = allowedTypes
    .map((t) => `[data-field-type="${t}"]`)
    .join(', ')

  return [
    '/* werkflow palette filter — generated */',
    `.fjs-palette-field${hiddenTileSelector} { display: none !important; }`,
    // Hide entire group when it has no descendant visible tile.
    `.fjs-palette-group:not(:has(${visibleTileSelector})) { display: none !important; }`,
  ].join('\n')
}

/**
 * Injects (or updates) a palette-filter `<style>` block inside `container`.
 *
 * Safe to call multiple times — re-uses the existing element by id so
 * repeated `importSchema` calls don't stack duplicate `<style>` nodes.
 *
 * Call this directly from the component after `importSchema` resolves,
 * e.g.:
 *   await editor.importSchema(schema);
 *   injectPaletteFilter(containerRef.current, allowedTypes);
 *
 * @param container  - The same HTMLElement passed to `new FormEditor({ container })`.
 * @param allowedTypes - Field type strings to keep visible in the palette.
 */
export function injectPaletteFilter(container: HTMLElement, allowedTypes: string[]): void {
  const existing = container.querySelector(`#${STYLE_ID}`)
  if (existing) {
    existing.textContent = buildHideCss(allowedTypes)
    return
  }
  const style = document.createElement('style')
  style.id = STYLE_ID
  style.textContent = buildHideCss(allowedTypes)
  container.appendChild(style)
}
