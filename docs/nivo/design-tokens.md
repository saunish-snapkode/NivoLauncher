# Nivo design tokens

Source of truth: `data/NivoPaletteSpec.kt` (raw colours, unit-testable) and `ui/design/NivoTokens.kt` (Compose).

## Palettes
| Token | Light | Dark | AMOLED |
|---|---|---|---|
| canvas | #F7F7F4 | #0B0D10 | #000000 |
| surface | #FFFFFF | #15181D | #0A0B0D |
| elevated | #FFFFFF | #1D2127 | #131519 |
| highest | #EDEDE9 | #272C33 | #1C1F24 |
| text primary | #18191B | #F5F6F7 | #F5F6F7 |
| text secondary | #686B70 | #9EA3AB | #9EA3AB |
| hairline | #E8E9E7 | #262A31 | #1F2227 |

`nivoColorScheme()` maps a palette + one accent onto a Material `ColorScheme`, so every screen inherits it.
Accent presets: Indigo, Blue (default), Teal, Green, Orange, Red, Pink, Purple. Other colour styles:
wallpaper (Material You) and a custom-accent scheme.

**Contrast is tested** (`DesignAndLibraryTest`): primary text ≥ 7:1 and secondary text ≥ 4.5:1 on canvas,
surface and elevated for all three palettes.

## Scales
* Space: 2 / 4 / 8 / 12 / 16 / 24 / 32 / 48 dp (`NivoSpace`)
* Radius: inner 6, control 14, card 20, sheet 28, dock 28, hero 28 (`NivoRadius`); Material `shapes` map onto these
* Elevation: chrome 0, content 0, interactive 2, modal 6 (`NivoElevation`)
* Touch: every tappable element is at least 48 dp (`NivoTouch.min`), even when it draws smaller
* Emphasis alpha: high 1, medium 0.74, low 0.56, disabled 0.38, hairline 0.12 (`NivoAlpha`)
* Press feedback: `Modifier.pressFeedback(interactionSource)` sinks an element while pressed, scaled by the motion preset
* Type (`nivoTypography`): light 36–57sp display for numerals, restrained Medium weights for titles/labels.
  The system sans-serif is used; no proprietary font is bundled. User font choice (incl. custom file) overrides it.

## Known gaps
Blur tokens, scheduled theme switching and per-surface material variants are not implemented yet.
Some older screens still use literal dp values; they are being migrated to the tokens.
