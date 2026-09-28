# MineColonies — Community Port for Minecraft 26.1.x and newer(NeoForge)

> An unofficial, GPL-3.0 licensed community port of [MineColonies](https://github.com/ldtteam/minecolonies)
> (and its companion libraries) from Minecraft **1.21.1 / NeoForge 21.1.x** to
> **Minecraft 26.1.2 and newer**.

MineColonies is an interactive town-building mod: create your own colony with
50+ buildings in over 20 styles, hire and level up NPC workers (Builders, Farmers,
Guards, Bakers, …) and defend your citizens from raids. This project brings that
experience to the current Minecraft version line as a **single, self-contained mod**
("merged port") so you do not need to install five separate ldtteam mods.

**This is not an official MineColonies release.** It is maintained independently by
the community. For the official mod, bugs and feature requests, please use the
[upstream project](https://github.com/ldtteam/minecolonies).

---

## Status

Current port release: **`1.1.1399-port26.1.2-0.6.0`** (see `gradle.properties`).

| Component | Base (1.21.1) | Port target |
|---|---|---|
| MineColonies | `1.1.1399-1.21.1-snapshot` (ldtteam — latest 1.21.1 snapshot release) | MC `26.1.2` |
| Structurize | `1.0.833-1.21.1-snapshot` (pinned) | merged into this mod |
| BlockUI | `1.0.212-1.21.1-snapshot` (pinned) | merged into this mod |
| Domum Ornamentum | `1.0.236-snapshot` (pinned) | merged into this mod |
| Multi-Piston | `1.2.58-1.21.1` (pinned) | merged into this mod |

The port tracks the **latest 1.21.1 sources of the upstream project** and re-targets
them to the 26.1 toolchain. Upstream code is kept as close to original as possible —
changes are mechanical (API migrations, loader gates, package merges), not functional rewrites.

## Installation

1. Install NeoForge version that matches the port.
2. Download the port jar from [Releases](../../releases) (the *published* jar — see
   [Two jar flavours](#two-jar-flavours)) and drop it into your `mods/` folder.
3. Start the game. On the title screen you will see **"MineColonies port — external
   assets"**: click **[Download now]** to fetch the official MineColonies asset jar
   plus the optional style packs (Byzantine, StyleColonies) and TownTalk voices
   straight from the CurseForge CDN (see [ARR policy](#arr-policy) below for why).
4. Optional: add [TownTalk](#towntalk-compatibility) support by patching your own
   official TownTalk jar with `tools/towntalk-patch.mjs`.

No other mods are required — Structurize, BlockUI, Domum Ornamentum and Multi-Piston
are merged into this jar under their original GPL-3.0 license.

## Optional packs: building styles & citizen voices

The same first-run screen can also fetch three *optional* official packs (or, for
offline machines, you drop their official jars into
`<gamedir>/port-assets/source-jars/` and press **[Download now]** — they are then
extracted locally with no network access):

| Pack | What you get | Source |
|---|---|---|
| **Byzantine Styles Pack** | 4 extra building styles: `Byzantine_for_1.20`, `Nile`, `Rebel_Base`, `Shogun` (4 864 blueprints) | CurseForge project 974855 |
| **StyleColonies** | 13 extra building styles: Antique, Crimson Keep, Farthest Frontier, Functional Fantasy, High Magic, Hive, Tropical, Aquatica, Corrupted, Fairytale, Frontier, Steampunk, Underwater Base (5 304 blueprints) | CurseForge project 827507 |
| **TownTalk** | citizen voices — see the [compatibility note](#towntalk-compatibility) | CurseForge (official page) |

These packs are integrated as **external styles**: the port scans the extracted
blueprints and registers them as additional colony building styles — nothing is
bundled in the mod jar (they are ARR, see below).

### First run without internet

If the machine playing has no internet access: put the official
`minecolonies-1.1.1399-1.21.1-snapshot` jar into
`<gamedir>/port-assets/source-jars/`, restart the game and press **[Download]** —
assets are extracted locally without any network access.

## Two jar flavours

| Command | Jar | Use |
|---|---|---|
| `./gradlew build` | **dev jar** | local testing only; contains *all* assets, including MineColonies' ARR assets |
| `./gradlew publishJar` | **publish jar** | the one you publish / install; **no ARR content at all** |

Only ever distribute jars produced by `publishJar`.

## ARR policy (important — read before redistributing)

- **Code of MineColonies, Structurize, BlockUI, Domum Ornamentum, Multi-Piston** is
  GPL-3.0 → merged and redistributed here under GPL-3.0, which is legal and intended
  by the upstream authors. Their *assets and datapack content that are GPL'd* are
  embedded as well.
- **MineColonies art assets (textures, models, sounds, blueprints, generated data,
  CrowdIn translations) are ARR (All Rights Reserved)**. They are **not** contained
  in the published jar. Instead, the player's game downloads the official MineColonies
  release jar from the CurseForge CDN at first run and extracts the assets locally
  (`port-assets/`), injected as an always-on resource + data pack.
- **TownTalk** is ARR and never redistributed: users patch their *own* official
  TownTalk jar locally with `tools/towntalk-patch.mjs` (loader gates only).
- **Byzantine** and **StyleColonies** style packs are ARR as well and never bundled
  in this repository or its jars — players fetch them from their official CurseForge
  pages through the same first-run screen, exactly like every other MineColonies
  player would.
- Nothing from Mojang / Microsoft is redistributed. Minecraft is a trademark of
  Mojang Synergies AB. This project is not affiliated with, endorsed by, or sponsored
  by Mojang, Microsoft, CurseForge or the MineColonies team.

## TownTalk compatibility

The official **TownTalk 1.2.0 (MC 1.21.1)** will not load next to the merged port
(hard gates on `minecraft [1.21,1.22)` and on the separate ldtteam modIds). The
bundled patcher rewrites **only** the version gates in `neoforge.mods.toml` and
re-points the dependencies to the merged `minecolonies` modId — the rest of the jar
is copied byte-for-byte. Because TownTalk's compiled code still targets the 1.21.1
API, runtime behaviour on 26.1.2 is not guaranteed; test in-game before relying on it.

```bash
node tools/towntalk-patch.mjs towntalk-1.2.0-1.21.1.jar
# -> towntalk-1.2.0-1.21.1-patched.jar
```

## Building from source

Requirements: **JDK 25+** (NeoForge Gradle toolchain is fetched automatically).

```bash
git clone https://github.com/Forgotten409/NeoForge-MineColonies-Unofficial.git
cd NeoForge-MineColonies-Unofficial
./gradlew build          # dev jar  -> build/libs/
./gradlew publishJar     # publish jar (ARR-free) — the only jar you may distribute
```

Optional compat mods (JEI, Dynamic Trees, JourneyMap) are **dev-time
dependencies fetched automatically**: JEI and the JourneyMap client API come
from `maven.blamejared.com`, the full Dynamic Trees and JourneyMap mods from
[CurseMaven](https://cursemaven.com) (a proxy of the official CurseForge files).
No manual downloads are needed; a local `libs/` folder with your own jars is
supported as an offline fallback and is gitignored.

The cloned tree is ARR-free on purpose (see below): the dev jar builds fine and
the game provisions external assets at first run — **`./gradlew runClient` on a fresh
clone works out of the box**: the port-authored 26.1 item definitions, egg art,
equipment models and novel lang keys ride along on the dev classpath (via
`src/main/resources-publish`), and the ARR art + data arrive through the first-run
download screen, exactly like the published jar. For fully-offline dev — or to
re-create the maintainer's bundled local dev layout — additionally run:

```bash
node tools/restore-dev-assets.mjs --bundled-flag   # download the official jar, convert 1.21.1→26.1, bundle locally
node tools/restore-dev-assets.mjs --clean          # remove the local ARR assets again
```

For the novel-language extraction and TownTalk tooling see `tools/` in this repo.

## Publishing the source to GitHub

The repository is **safe to push as-is**: `.gitignore` blocks every ARR path
(MineColonies art/sounds/blueprints/generated data) so `git add .` can never
pick them up, and a guard verifies the tree before every push:

```bash
node tools/check-arr-clean.mjs      # MUST print OK before every push
```

If ARR content ever made it into the git **history** (e.g. from an earlier
push), the simple fix is to delete the GitHub repository and re-push from a
clean tree, or rewrite the history with `git filter-repo`.

`tools/restore-dev-assets.mjs` can
additionally re-create the maintainer's local dev layout (it downloads the official
asset jar from the CurseForge CDN and converts it to the 26.1 formats — fully usable
offline with `--bundled-flag`).

## License

- **This repository (the port): GPL-3.0** — see [`LICENSE`](LICENSE). The port is a
  derivative work of five GPL-3.0 projects and therefore must remain GPL-3.0.
- Third-party attribution and the exact ARR boundary: [`LICENSE-THIRD-PARTY.md`](LICENSE-THIRD-PARTY.md).
- Short attribution notice: [`NOTICE`](NOTICE).

## Credits

- **[LDT Team](https://ldtteam.com)** and every MineColonies contributor — the entire
  colony simulation, its content and years of work. Please consider
  [supporting them on Patreon](https://www.patreon.com/minecolonies).
- Upstream projects: [minecolonies](https://github.com/ldtteam/minecolonies) ·
  [Structurize](https://github.com/ldtteam/Structurize) ·
  [BlockUI](https://github.com/ldtteam/BlockUI) ·
  [Domum Ornamentum](https://github.com/ldtteam/domum-ornamentum) ·
  [Multi-Piston](https://github.com/Multipiston/Work).
- The NeoForge team for the 26.1 toolchain.

Port maintenance: community (see commit history). "MineColonies" name and assets are
© their respective right holders and used here only for compatibility/attribution purposes.
