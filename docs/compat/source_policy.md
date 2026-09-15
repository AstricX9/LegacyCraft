# Source policy

LegacyCraft targets observable equivalence with vanilla **Minecraft: Java Edition
1.8.9** (protocol 47, resource-pack format 1). The full rules live in
[`MINECRAFT_1_8_9_REPLICATION_MASTER_CONTEXT.md`](MINECRAFT_1_8_9_REPLICATION_MASTER_CONTEXT.md)
(mirrored in the LegacyCraft claude.ai project under `docs/`). This file is the
short version agents should check before writing a compatibility-sensitive line.

## Evidence hierarchy (highest to lowest authority)

0. **Vanilla 1.8.9 black-box observation** — run the real client/server the user
   supplies, capture behavior, compare.
1. **Official Mojang artifacts/metadata** — the `version.json` and asset archive
   already bundled under `src/main/resources/assets` (user-supplied, not
   redistributed by this repo — see `.gitignore` / README).
2. **Version-specific protocol docs for protocol 47** — an *archived* wiki.vg
   snapshot from ~2015–2016, not the live minecraft.wiki protocol pages (those
   now document current protocol versions). See `docs/reference_sources.md` in
   the project for links.
3. **Minecraft Wiki version/history pages** — only the 1.8.x-era content.
4. **Community reference implementations** — for discovering edge cases only,
   never assumed faithful, never copied verbatim.
5. **Memory/forum anecdotes** — hypotheses only, must become a test before
   being treated as true.

## Tagging

Tag nontrivial compatibility claims in comments/commit messages:
`VERIFIED_VANILLA`, `VERIFIED_FORMAT`, `VERIFIED_HISTORY`,
`COMMUNITY_CORROBORATED`, `HYPOTHESIS`, `UNKNOWN`. Never silently promote
`HYPOTHESIS` to fact — move it to `unknowns.yml` first if it needs resolving.

## IP boundary

- Never paste, reconstruct, or redistribute Mojang source code.
- Never commit Mojang textures/sounds/music/fonts/language files/models —
  `src/main/resources/assets` is user-supplied at build/run time and stays out
  of version control (confirm `.gitignore` covers it).
- Community decompilations/mappings are research pointers only.
- No official-affiliation branding.

## Companion files in this directory

- `verified_facts.md` — anchors confirmed against Tier 0–3 sources.
- `unknowns.yml` — open questions blocking full fidelity (per master doc §89).
- `bugs_1_8_9.yml` — vanilla quirks/bugs to intentionally preserve (§88).
- `deviations.yml` — every place LegacyCraft currently differs from vanilla
  1.8.9 on purpose or as a known placeholder (§111 — "no hidden deviations").
