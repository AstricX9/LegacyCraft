# LegacyCraft

A from-scratch Java/LWJGL recreation of the Minecraft 1.8.9 survival experience.

## Run

```powershell
.\gradlew.bat run
```

## Controls

- `WASD` move, mouse look
- `Space` jump, `Left Shift` sneak, `Left Control` sprint
- Left click break block, right click place block
- Mouse wheel or `1`-`9` select a hotbar slot
- `F` toggle fog distance, `F2` save a screenshot
- `F3` toggle the debug text overlay
- `Esc` release/capture the mouse; press it again while released to quit

The game resources under `src/main/resources/assets` come from the user-supplied
Minecraft 1.8.9 asset archive and are not redistributed by this project setup.

The subsystem layout and fidelity rules are tracked in [ARCHITECTURE.md](ARCHITECTURE.md).
