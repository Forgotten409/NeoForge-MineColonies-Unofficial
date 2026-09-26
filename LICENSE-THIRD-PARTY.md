# Third-party licenses & ARR boundary

This port merges code from five upstream projects, all licensed **GPL-3.0**
(full text in [`LICENSE`](LICENSE) — identical upstream text is preserved verbatim):

| Project | Upstream | Version merged | License | Embedded content |
|---|---|---|---|---|
| MineColonies | https://github.com/ldtteam/minecolonies | `1.1.1387-1.21.1-snapshot` | GPL-3.0 | **code only** (assets are ARR, see below) |
| Structurize | https://github.com/ldtteam/Structurize | `1.0.832-1.21.1` | GPL-3.0 | code + assets + data |
| BlockUI | https://github.com/ldtteam/BlockUI | `1.0.199-1.21.1-snapshot` (repo: `1.0.212-snapshot`) | GPL-3.0 | code + assets + data |
| Domum Ornamentum | https://github.com/ldtteam/domum-ornamentum | `1.0.223-snapshot` (repo: `1.0.236-snapshot`) | GPL-3.0 | code + assets + data |
| Multi-Piston | https://github.com/Multipiston/Work | `1.2.51-1.21.1-snapshot` | GPL-3.0 | code + assets + data |

> Merged GPL content keeps its upstream copyright and license notice. Where a file
> is derived from upstream, the git history of this repository identifies the origin
> snapshot. The GPL-3.0 requires the combined work to be distributed under GPL-3.0,
> which this repository does.

## ARR (All Rights Reserved) — never in the published jar

The following MineColonies content is **not** GPL-licensed and **must not** be
redistributed inside any jar published from this repository:

- `assets/minecolonies/**` — textures, models, sounds, blockstates, atlases, lang
- `data/minecolonies/**` — generated datapack content (recipes, advancements, …)
- `data/c/**` (CrowdIn), `data/dynamictrees/**`, `data/neoforge/**` (generated)
- `blueprints/**` — structure blueprints
- `minecolonies.png` (mod logo)

At first run, the game downloads the **official** MineColonies jar from the
CurseForge CDN (`mediafilez.forgecdn.net`), extracts `assets/`, `data/` and
`blueprints/` to `<gamedir>/port-assets/minecolonies/` and injects them as an
always-on resource pack + data pack. Nothing ARR is ever shipped by this project.
The development jar (`./gradlew build`) does bundle ARR assets for **local testing
only** — do not distribute it.

## TownTalk

TownTalk (https://www.curseforge.com/minecraft/mc-mods/towntalk) is **ARR**. This
project does not contain any TownTalk content. `tools/towntalk-patch.mjs` is a
local helper that rewrites loader gates in a jar the *user already owns*; the
patched jar is produced and stays on the user's machine.

## Byzantine & StyleColonies (external style packs)

**Byzantine Styles Pack** (CurseForge project 974855) and **StyleColonies**
(CurseForge project 827507) are **ARR** building-style packs. This repository
contains none of their blueprints or code. The first-run screen offers to
download them from their official CurseForge distribution, exactly like a
player would; the port then scans the extracted `blueprints/byzantine/**` and
`blueprints/stylecolonies/**` sets and registers them as additional colony
building styles. The dev tree therefore never contains them either — the same
rule as the MineColonies ARR assets above (`blueprints/**` is fully gitignored).

## Dynamic Trees, JourneyMap, JEI (optional compat mods)

The port has *optional* compatibility with **Dynamic Trees**, **JourneyMap**
and **JEI** (relations declared `optional` in `neoforge.mods.toml`). None of
these mods is bundled in the published jar or committed to this repository —
players who want the compat features install them from their official
CurseForge pages. For development, the build fetches them automatically
at build time: JEI and the JourneyMap client API from `maven.blamejared.com`,
the full Dynamic Trees and JourneyMap mods from CurseMaven (a proxy of the
official CurseForge files by numeric id — the same files players download).
A `libs/` folder with locally downloaded jars is supported as an offline
fallback and is gitignored.

## Not affiliated

Minecraft is a trademark of Mojang Synergies AB. NeoForge is a project of the
NeoForged community. This port is not affiliated with, endorsed or sponsored by
Mojang, Microsoft, the NeoForged team, CurseForge or the MineColonies team.
