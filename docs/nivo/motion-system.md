# Nivo Motion

`ui/design/NivoMotion.kt` defines presets (Default, Smooth, Snappy, Calm, Reduced). Call sites ask
`LocalMotion.current` for springs (`motion.spring(weight)`), press scale, stagger and parallax flags.

| Preset | Feel |
|---|---|
| Default | balanced spring (stiffness 700, damping 0.85) |
| Smooth | softer, longer settles |
| Snappy | quick, no staggered entrance |
| Calm | slow, no page parallax |
| Reduced | short fades only: no bounce, scale, stagger or parallax |

Reduced is also forced automatically when Android's system animator scale is 0.

## Where motion is applied
Icon press feedback, icon entrance (staggered), page parallax, icon re-placement springs, drag lift,
drawer settle (stiffness per preset), folder open, App Library card expansion, settings page transitions.

## Engineering notes
* Drawer and library animations are state-driven and interruptible; geometry is clamped because springs overshoot.
* The clock ticks on minute boundaries and stops when the activity is not STARTED.
* Haptics: a single tap on pick-up, user-toggleable (`LocalHapticsEnabled`).

## Not yet done
Frame-time profiling on a high-refresh device, shared-element folder expansion from the exact icon origin,
page-overview scaling, theme cross-fade.
