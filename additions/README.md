# Additions: LegacyCraft's mod system

An addition contains its definition **and executable logic**. The engine only
provides loading, settings, and public hooks. There is no built-in implementation
fallback for a missing module JAR.

```text
additions/
  legacyConsoleGraphics.json
  legacyConsoleGraphics/
    addition.jar
    src/main/java/...
    src/test/java/...
  shaderPacks.json
  shaderPacks/
    addition.jar
    src/main/java/...
  _template.json
  _template/src/main/java/example/MyAddition.java
```

Every supplied game and launcher addition follows this layout. Source, rendering
code, shaders, helpers, and feature tests belong to their module folders. The
compiled classes are excluded from the game and launcher JARs.

## Create an addition

1. Copy `_template.json` to `myAddition.json` and `_template/` to `myAddition/`.
2. Set the manifest's `id`, `name`, category and settings. Keep `api: 1`.
3. Set `entrypoint` to the public Java class implementing `ClientAddition` and
   `code` to its JAR path relative to this folder, such as
   `myAddition/addition.jar`.
4. Implement the desired hooks in `myAddition/src/main/java/`.
5. Run `gradlew buildAdditions`, then launch the game. The new entry is discovered
   automatically; no change to `Minecraft.java`, a switch statement, or registry
   of built-in feature classes is required.

The build discovers source modules from their manifests and emits a separate
JAR per addition. A prebuilt third-party JAR with no source directory is left
untouched. `gradlew run`, tests, and distribution tasks build source modules as
needed. To build only one, use `gradlew buildAdditionLegacyConsoleGraphics`.
Executable JARs and build caches are ignored by Git; source and manifests are
versioned. Packaged builds contain both the module JARs and their source.

## Public hooks

Game entry points implement
`net.minecraft.client.additions.api.ClientAddition`. Its optional hooks cover
input, reset, HUD/crosshair drawing, camera FOV and hurt tilt, night-vision
brightness, graphics profiles, shader backends, rendering after the world, extra
settings-screen actions, and resource cleanup. `AdditionContext` exposes the
module's own switch, settings, metadata and picked files.

Callbacks are gated by the module's switch and the Additions master switch.
Input callbacks also run while disabled so queued presses can be discarded;
check `context.enabled()` before changing input. Implement `reset()` for latched
state and `close()` for owned resources. GUI hooks run with the game context
current. Rendering callbacks run on the render thread.

`GraphicsProfile` supplies optional lightmap, atmosphere, fog and sky callbacks.
`Shaders.Backend` is the optional render-pipeline API; the engine uses a no-op
backend when none is installed. The console and shader-pack implementations
live entirely in their respective modules. Module resources can be read using
the entry-point class's standard `getResourceAsStream` methods.

Launcher entry points implement
`net.legacycraft.launcher.api.LauncherAddition`, compiled against the launcher
API. Hooks cover initial-tab selection, startup notifications, and extra
instance-summary text. Launcher modules do not depend on client classes.

Each JAR has its own class loader with the host API as its parent. New modules
can use arbitrary IDs and entry-point class names. Unknown API versions, missing
JARs and invalid entry points are reported without preventing the host from
starting. A failing dispatched hook disables that module for the current run.

## Install, remove and configure

Copy a manifest and its module folder/JAR into `additions/`, then restart the
host. Removing the definition or executable removes its behaviour; the engine
has no replacement feature code. An instance's own `additions/` folder replaces
the installation catalog, so copy each desired manifest **and module JAR** there.
An empty catalog intentionally contains no mods.

Switches and settings remain in the instance's `additions.txt`. Existing IDs and
setting keys are unchanged. Slider/toggle changes still apply live; changing
Java code, adding/removing modules, or replacing a JAR requires a restart. Saved
choices survive removal and restoration. Underscore-prefixed definitions are
ignored. Both host JARs carry metadata fallback copies, but executable logic is
only loaded from external module JARs.
