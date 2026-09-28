# Testing standard

Every test in `src/test/java` follows these rules.

## Running

| Command | What runs |
|---|---|
| `gradlew test` | The fast suite: every test except `@Oracle` ones. Run it before committing. |
| `gradlew oracleTest -Dlegacycraft.vanillaWorld=C:/Server/world` | The `@Oracle` comparisons against a real 1.8.9 world. They skip themselves when that world is missing. |
| `gradlew test --tests "*SkyRendererTest"` | A single class. |

## Layout and naming

- A test lives in the **same package** as the class it tests, named `<ClassUnderTest>Test`. One subject per file.
- Test classes are **package-private and `final`**: `final class WorldTest`.
- Each class starts with a **one-line Javadoc** saying what it covers. Add the evidence tag (`HYPOTHESIS`, `VERIFIED_VANILLA`, ...) when the expected values come from recalled vanilla behaviour rather than from a comparison.
- Test methods are **behaviour sentences in camelCase** with no `test` prefix: `stairsRiseAwayFromThePlayerAndFlipOnUpperHits`.
- Assertions use the static wildcard import `import static org.junit.jupiter.api.Assertions.*;`.
- Imports come in three sorted groups: project and library, then `java.*`, then static.

## Shared fixtures (`net.minecraft.testing`)

- **`GameData`** holds the block, biome and item registries and the world-generation block tables. They are loaded once per run, so tests call `GameData.blocks()` and similar instead of loading their own copy. `GameData.newWorld(seed)` gives a fresh world. Only the loader tests (`BlockRegistryTest`, `ItemRegistryTest`, ...) call `load(...)` themselves.
- **`VanillaWorld`** is the path of the real 1.8.9 world. `VanillaWorld.assumePresent()` skips a test when the world isn't there, instead of failing it.
- **`@Oracle`** goes on a class that compares against `VanillaWorld`. It tags the class `oracle`, so `gradlew test` leaves it out and `gradlew oracleTest` runs it.
- Package-local helpers stay beside the tests that use them: `item/TestItems`, `block/state/TestBlockAccess`.

## What belongs in the suite

- **Every test asserts something.** Diagnostic probes that only print numbers are run once to answer their question and then deleted; they stay in the git history.
- A test that needs a vanilla reference is an `@Oracle` test, never a hard-coded path. It must skip cleanly without the reference.
- A test that pins recalled vanilla numbers says so in its Javadoc (`HYPOTHESIS`), so a later comparison knows what to replace.
- Keep tests headless: no OpenGL context and no window. Test the maths behind rendering, not the drawing.
