# Legacy Console Graphics

Enable **Additions > Graphics > Legacy Console Graphics**. The gear opens the
standard Additions settings screen. All controls apply on the next world frame;
closing the screen saves them to `additions.txt`. The launcher reads the same
catalog and per-instance settings. The addition and the Additions master switch
both default to OFF.

This is an artistic approximation of the classic console appearance, not a
verified reproduction of 4J's proprietary renderer or measured console gamma.
Its source and executable JAR live in `additions/legacyConsoleGraphics/`; the
manifest points to its entry class. The engine contains only rendering hooks.
The module modifies the native lightmap, OpenGL distance fog, sky geometry and cloud
colours. A built-in GPU colour transfer applies gamma and contrast to rendered
world colours, including texture detail and face shading, before drawing the
HUD or menus. It does not require a shader pack or resource pack.

| Setting | Default | Effect |
| --- | --- | --- |
| Fog Start | 35% | Percentage of render distance where terrain starts fading |
| Fog End | 90% | Percentage where terrain fully fades; capped one chunk inside render distance |
| Console Gamma | 25% | Midtone lift of world colours, including fully sunlit textures |
| Daylight Warmth | 35% | Subtle warm daylight, reduced at night and during rain |
| Contrast | 20% | World contrast: darkens shadows and brightens highlights |
| Water Visibility | 100% | Underwater visibility; 50–150% range |

Crossed fog sliders are resolved by moving the start below the end, retaining at
least eight blocks of fade. Fog follows biome sky colour and the existing
directional sunrise/sunset colours, cave darkening and void fade. A sky dome
blends from the fog colour at and below the horizon to the overhead sky.
Rain and thunder ease in/out over five seconds at 20 ticks/second, desaturating
and darkening sky, fog, clouds and outdoor lighting. Weather already active on
world entry initializes immediately. Night retains a cool tint; torchlight stays
warm. Original directional block-face shading and ambient occlusion are retained.

Water uses blue exponential fog with configurable visibility; lava retains its
dense orange/red fog. Surface weather does not recolour either liquid. Blindness
overrides distance fog and darkens the sky. Night Vision and Full Bright still
work through the lightmap. The Nether retains its red palette and dense fog;
the End retains its original sky and purple fog.

The console profile takes priority over Shader Packs while enabled. The selected
pack remains saved and resumes when the profile is disabled. Switching between
pack rendering and native rendering uses the existing mesh rebuild mechanism.
OFF restores the original sky, fog and lightmap paths without changing Options.

## Verification

Run `gradlew test` with Java 17 (this builds external modules). Feature tests
live in the module's `src/test/java`. Focused headless coverage is in
`LegacyConsoleGraphicsTest`, `LightMapTest`, `ConsoleColorGradingTest`, `SkyRendererTest`, and `AdditionsTest`.
It covers persistence, master-switch gating, live table invalidation, exact
lightmap restoration, fog limits, liquids, blindness, weather interpolation,
time-of-day continuity, horizon colour agreement, and visible gamma/contrast
changes on texture colours with a fully bright lightmap.

For visual comparison, use the same world, camera, render distance and brightness
with the addition OFF and ON. Check noon, dawn/dusk facing toward and away from
the sun, midnight, rain/thunder, a torch-lit cave, water/lava, and Nether/End.
Test both ends of the fog sliders at short and long render distances; switch
from an active shader pack to the console profile and back. Verify that the
HUD/menu colours and the up/down/north/east face-shading differences stay intact.
Fog uses the existing fixed-function distance calculation (eye-radial when the
driver supports `GL_NV_fog_distance`, eye-plane otherwise). The eye-plane fallback
shortens its range according to the field of view and aspect ratio, so the sides
of a wide view also fade before the circular terrain boundary.
