# Design System Guidelines

Version 1.1 · Material 3 Expressive · Maroon brand · Light + Dark. Values marked **[SET]** are still to be provided.

## 0. Design Direction

| Decision | Choice |
|---|---|
| Design language | Material 3 Expressive |
| Brand color | Maroon `#800000`, same in light and dark |
| Light theme | White base |
| Dark theme | True black base |
| Density | Comfortable, light visual weight |
| Performance | 60fps target, flat tonal surfaces, spring motion on transform/opacity only |

## 1. Principles

1. **Clarity first.** One primary action per view; no ambiguity.
2. **Consistency.** Same pattern, same behavior, everywhere.
3. **Efficiency.** Fewest steps to complete a task.
4. **Accessibility by default.** WCAG 2.2 AA minimum.
5. **Feedback always.** Every action gets a visible response.
6. **Token-driven.** No hard-coded values in design or code.

## 2. Foundations

### 2.1 Color

**Brand palette:** Primary is Maroon `#800000` (`primary.600`), identical in both themes. Neutrals: white (light), black (dark). The tonal scale (900 `#3D0000` · 800 `#590000` · 700 `#6B0000` · 600 `#800000` · 500 `#9B1C1C` · 400 `#B84545` · 300 `#D9807F` · 200 `#EBB0AF` · 100 `#F7DAD9` · 50 `#FDF1F0`) is a proposed starting point; verify contrast with a checker before release.

| Role | Token | Light | Dark |
|---|---|---|---|
| Primary (fill) | `color.primary` | `#800000` | `#800000` |
| On primary | `color.onPrimary` | `#FFFFFF` | `#FFFFFF` |
| Primary container | `color.primaryContainer` | `#F7DAD9` | `#590000` |
| On primary container | `color.onPrimaryContainer` | `#3D0000` | `#F7DAD9` |
| Primary accent (text, icon, outline, focus) | `color.primaryAccent` | `#800000` | `#D9807F` |
| Secondary container | `color.secondaryContainer` | `#F1E4E3` | `#3A2A2A` |
| Success | `color.success` | `#2E7D32` | `#66BB6A` |
| Warning | `color.warning` | `#ED6C02` | `#FFB74D` |
| Error | `color.error` | `#D32F2F` | `#FF6B6B` |
| Info | `color.info` | `#0277BD` | `#4FC3F7` |

**Semantic tokens (use these in UI, never raw scale values):**

| Token | Light | Dark |
|---|---|---|
| `bg.page` | `#FFFFFF` | `#000000` |
| `bg.surface` | `#FFFFFF` | `#0D0D0D` |
| `surface.container.low` | `#FAF6F6` | `#121212` |
| `surface.container` | `#F4EEEE` | `#161616` |
| `surface.container.high` | `#EDE6E6` | `#1F1F1F` |
| `text.primary` | `#1C1B1B` | `#F5F5F5` |
| `text.secondary` | `#5F5757` | `#B8B8B8` |
| `text.disabled` | `#A39B9B` | `#6B6B6B` |
| `border.default` | `#D8CECE` | `#2E2E2E` |
| `border.focus` | `#800000` | `#D9807F` |

**Rules**

- Text contrast ≥ 4.5:1 (body), ≥ 3:1 (large text ≥ 18px or 14px bold, and UI components).
- Never use color alone to convey meaning; pair with icon or text.
- Ratio guideline: 60% neutral, 30% secondary/surface, 10% primary/accent.

**Maroon rules**

- Maroon `#800000` on black is about 1.9:1 and fails 3:1. In dark theme use it only as a filled surface with white content; use `primaryAccent` (`#D9807F`, about 7:1 on black) for text, icons, outlines and focus rings.
- White on maroon is about 10.9:1 (passes AAA).
- Error red must stay distinct from maroon: always pair with icon + text; destructive buttons use outlined or tonal error style, never a filled maroon button.
- One filled maroon element per region.

### 2.2 Typography

**Font family:** Roboto Flex (variable font), fallback `system-ui, sans-serif`. Use Expressive emphasized styles (heavier weight, larger size) for key headlines and primary actions. Monospace optional for code and numbers.

| Style | Size / Line height | Weight | Use |
|---|---|---|---|
| Display | 40 / 48 | 700 | Hero, landing |
| H1 | 32 / 40 | 700 | Page title |
| H2 | 24 / 32 | 600 | Section |
| H3 | 20 / 28 | 600 | Subsection |
| H4 | 16 / 24 | 600 | Card title |
| Body L | 18 / 28 | 400 | Long-form |
| Body | 16 / 24 | 400 | Default |
| Body S | 14 / 20 | 400 | Secondary text |
| Caption | 12 / 16 | 400 | Meta, helper text |
| Button/Label | 14 / 20 | 500–600 | Controls |

**Rules**

- Minimum body size 14px (mobile 16px for inputs to prevent zoom).
- Line length 45–75 characters.
- Max 3 weights in use. No more than 2 type sizes per component.
- Use sentence case for UI text; ALL CAPS only for small labels.

### 2.3 Spacing

Base unit **4px**; primary rhythm **8px**.

| Token | Value |
|---|---|
| `space.1` | 4 |
| `space.2` | 8 |
| `space.3` | 12 |
| `space.4` | 16 |
| `space.5` | 24 |
| `space.6` | 32 |
| `space.7` | 48 |
| `space.8` | 64 |

**Rules**

- Related elements: 4–8. Grouped elements: 16. Sections: 32–48.
- Page padding: 16 (mobile), 24 (tablet), 32+ (desktop).
- Only token values; no arbitrary spacing.

### 2.4 Layout & Grid

| Breakpoint | Width | Columns | Gutter | Margin |
|---|---|---|---|---|
| Mobile | 0–599 | 4 | 16 | 16 |
| Tablet | 600–1023 | 8 | 24 | 24 |
| Desktop | 1024–1439 | 12 | 24 | 32 |
| Wide | 1440+ | 12 | 32 | auto (max-width 1280) |

Design mobile-first. Content never touches viewport edges.

### 2.5 Radius

| Token | Value | Use |
|---|---|---|
| `shape.xs` | 4 | Tooltips, badges |
| `shape.sm` | 8 | Chips, snackbars |
| `shape.md` | 12 | Inputs, small cards |
| `shape.lg` | 16–20 | Cards, menus |
| `shape.xl` | 28–32 | Dialogs, sheets, FAB |
| `shape.xxl` | 48 | Hero containers |
| `shape.full` | 9999 | Buttons, avatars, switches |

Expressive shape: morph shape on press or selection (round ↔ squarer) for buttons and toggles. No more than 3 shape sizes per screen.

### 2.5 Elevation

| Level | Use | Treatment |
|---|---|---|
| 0 | Page | `bg.page`, no shadow |
| 1 | Cards, resting surfaces | `surface.container.low` |
| 2 | Sticky bars, menus | `surface.container` |
| 3 | Dialogs, sheets | `surface.container.high` |

Elevation is tonal (surface color) in both themes. Optional soft shadow in light theme only, one per layer. No blur or backdrop filters.

### 2.6 Z-index

Base 0 · Sticky 100 · Dropdown 200 · Overlay 300 · Modal 400 · Toast 500 · Tooltip 600.

### 2.7 Motion

| Spring | Stiffness | Damping | Use |
|---|---|---|---|
| Spatial fast | 800 | 0.6 | Switches, buttons, chips (position, size, shape) |
| Spatial default | 380 | 0.8 | Cards, sheets, menus |
| Spatial slow | 200 | 0.8 | Full-screen transitions |
| Effects fast / default / slow | 3800 / 1600 / 800 | 1.0 | Opacity, color (no overshoot) |

Values are approximate; confirm against current Material 3 Expressive specs. Fallback when springs are unavailable: 150 / 250 / 350ms, `cubic-bezier(0.2, 0, 0, 1)`.

- Animate only `transform` and `opacity`; avoid layout-triggering properties.
- Springs for spatial change (overshoot allowed); none for color or opacity.
- Target 60fps (120 where supported); drop animation first on low-end devices.
- Max 2 concurrent animations per screen region.
- Respect `prefers-reduced-motion`: replace springs with fades ≤ 150ms.

### 2.8 Iconography

- One icon family, one style (outline *or* filled).
- Sizes: 16, 20, 24. Stroke weight consistent.
- Icon-only buttons require an accessible label and tooltip.
- Touch/click target ≥ 44×44px (min 24×24 on desktop per WCAG 2.2).

## 3. Components

Every component spec must define: **anatomy, variants, sizes, states, behavior, accessibility, do/don't.**

**Universal states:** default · hover · focus · active/pressed · disabled · loading · error · success.

### 3.1 Button

| Variant | Use |
|---|---|
| Primary | One per view; main action |
| Secondary | Supporting actions |
| Tertiary/Ghost | Low-emphasis actions |
| Destructive | Delete, irreversible |
| Link | Inline navigation |

- Sizes: S 32 · M 40 · L 48 (height). Horizontal padding 16.
- Label: verb-first, ≤ 3 words ("Save changes").
- Loading: spinner replaces icon, label kept, button disabled.
- Disabled: 40% opacity, no pointer events; prefer explaining why via helper text.
- Focus ring: 2px, offset 2px, contrast ≥ 3:1.

### 3.2 Text Input

- Anatomy: label · field · helper text · error text · optional prefix/suffix icon.
- Height 40 (default), 48 (touch-heavy).
- Label always visible above field; placeholder is a hint only, never a label.
- Error: red border + icon + message below; message states the fix.
- Mark optional fields "(optional)"; avoid asterisk-only required marking.

### 3.3 Selection Controls

| Control | Use |
|---|---|
| Checkbox | Multiple choices, independent |
| Radio | One of 2–5 mutually exclusive options |
| Switch | Immediate on/off effect |
| Select/Dropdown | One of 6+ options |
| Segmented control | 2–4 view modes |

### 3.4 Card

- Padding 16–24, `shape.lg`, elevation 1 or 1px border (not both).
- One primary action per card. Whole card clickable only if no inner actions.

### 3.5 Modal / Dialog

- Widths: S 400 · M 560 · L 800. Mobile: full-screen sheet.
- Must have: title, close control, primary + secondary action.
- Trap focus; Esc closes; return focus to trigger.
- Don't stack modals. Don't use for long forms.

### 3.6 Toast / Snackbar

- Duration 4–6s; persistent if it contains an action or error.
- Position consistent (e.g., bottom-center mobile, top-right desktop).
- Max one visible at a time; queue the rest.

### 3.7 Alert / Banner

- Types: info, success, warning, error. Icon + title + message + optional action.
- Inline for contextual issues; page-level banner for system-wide ones.

### 3.8 Navigation

| Pattern | Use |
|---|---|
| Top bar | Global brand, search, profile |
| Sidebar | 5+ primary destinations (desktop) |
| Bottom tab bar | 3–5 destinations (mobile) |
| Tabs | Switch views within a page |
| Breadcrumb | Hierarchies ≥ 3 levels |

- Always indicate current location (color + weight/indicator).
- Max 7 top-level items.

### 3.9 Table / List

- Row height: compact 40 · default 48 · comfortable 56.
- Left-align text, right-align numbers; sticky header on scroll.
- Required: sort, empty state, loading skeleton, pagination or infinite scroll.
- Mobile: convert to cards or horizontal scroll with pinned first column.

### 3.10 Other Core Components

Badge/Tag · Tooltip · Popover · Tabs · Accordion · Avatar · Progress bar · Skeleton · Pagination · Date picker · File upload · Search · Stepper · Empty state.

### 3.11 Material 3 Expressive Components

- **Buttons:** fully rounded by default; shape morphs on press and selection; add L and XL sizes for hero actions.
- **Button group:** connected buttons, 2px gap, selected item morphs.
- **Split button and toggle button:** for related or on/off actions.
- **FAB / FAB menu:** `shape.xl`, primary container; one per screen; extended FAB collapses on scroll.
- **Navigation bar:** 3–5 destinations, pill active indicator in primary container, labels always visible. Navigation rail on tablet and desktop.
- **Toolbar:** floating, tonal, for contextual actions.
- **Loading indicator:** morphing-shape indicator for short waits; wavy progress for determinate tasks.
- **Top app bar:** medium or large, collapses on scroll.
- **Selection:** use `primaryContainer` for selected states; reserve filled `primary` for the key action.

## 4. Patterns

### 4.1 Forms

- Single column; group related fields; logical tab order.
- Validate on blur, re-validate on change after first error; summarize errors at top on submit.
- Never clear user input on error.
- Show progress for multi-step flows.

### 4.2 Feedback States

| State | Treatment |
|---|---|
| Loading < 1s | Inline spinner / none |
| Loading 1–10s | Skeleton screen |
| Loading > 10s | Progress indicator + explanation |
| Empty | Illustration/icon + explanation + primary action |
| Error | What happened + how to fix + retry action |
| Success | Confirmation + next step |

### 4.3 Destructive Actions

- Require confirmation naming the item and consequence.
- Prefer undo over confirmation where feasible.

### 4.4 Search & Filtering

- Persistent search; show result count; clear-all filters; retain state on back navigation.

## 5. Accessibility (WCAG 2.2 AA)

- Full keyboard operability; visible focus on every interactive element.
- Semantic HTML / native roles first; ARIA only to fill gaps.
- Every image has alt text (or empty alt if decorative).
- Form fields have programmatically associated labels.
- Status changes announced via live regions.
- Support zoom to 200% and text resize without loss.
- No content flashing > 3 times/second.
- Test with a screen reader and keyboard-only on every release.

## 6. Content & Voice

- Tone: clear, direct, helpful. No jargon, no blame.
- Buttons: verb + object. Errors: cause + solution. Empty states: next step.
- Consistent terminology; maintain a glossary.
- Dates, numbers, currency formatted by locale.
- Plan for text expansion (+30–40%) for localization.

## 7. Theming & Dark Mode

- Components consume **semantic tokens only**.
- Provide light and dark maps; respect system preference with manual override.
- Re-check all contrast ratios in dark mode; use true black (#000000) for the page background and tonal surfaces for containers.

### 7.1 Performance and Lightness

- Flat tonal surfaces; no blur, backdrop filters or large shadows.
- Budget: 60fps, tap response < 100ms, first content < 1.5s on a mid-range device.
- One variable font file, subsetted, `font-display: swap`; inline SVG icons; WebP/AVIF images, lazy-loaded.
- Virtualize lists over 50 items; skeletons instead of spinners; optimistic UI for fast actions.
- Theme switching via CSS variables, no reload or flash.

## 8. Token Architecture

Three tiers:

1. **Primitive:** `maroon.600 = #800000`
2. **Semantic:** `color.action.primary = maroon.600`
3. **Component:** `button.primary.bg = color.action.primary`

Naming: `category.property.variant.state` (e.g., `color.text.secondary`, `button.primary.bg.hover`).

Example:

```json
{
  "color": {
    "primary": { "600": "#800000" },
    "action": { "primary": "{color.primary.600}" }
  },
  "space": { "4": "16px" },
  "shape": { "md": "12px" }
}
```

Single source of truth (JSON/Figma Variables) → exported to CSS variables / native platform styles.

## 9. Design Process & Handoff

1. Audit existing UI; list inconsistencies.
2. Define tokens.
3. Build core components with all states.
4. Document usage (do/don't, accessibility notes).
5. Pilot on one feature; refine.
6. Roll out; deprecate legacy styles.

**Handoff checklist:** tokens used · all states designed · responsive behavior · empty/loading/error states · accessibility annotations · copy finalized.

## 10. Governance

- **Versioning:** semantic (MAJOR breaking · MINOR additions · PATCH fixes).
- **Contribution:** proposal → design review → accessibility review → implementation → documentation → release.
- **Criteria for a new component:** needed in 2+ places; no existing component fits.
- **Deprecation:** announce one release ahead; provide migration path.
- **Changelog** maintained for every release.

## 11. Quality Checklist (per screen)

- [ ] Uses only tokens
- [ ] Contrast passes AA
- [ ] Keyboard navigable, focus visible
- [ ] All states covered (empty, loading, error, success)
- [ ] Works at mobile, tablet, desktop
- [ ] Dark mode verified
- [ ] Copy follows voice guidelines
- [ ] Touch targets ≥ 44px on mobile

## 12. Items to Provide

To finalize, supply: app name and platform (web, iOS, Android, Flutter), top 10 screens, and an optional tertiary accent color. The maroon tonal scale and neutral values above are proposed; verify them in a contrast checker.
