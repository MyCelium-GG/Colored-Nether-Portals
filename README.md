# Colored Nether Portals

[![License](https://img.shields.io/github/license/MyCelium-GG/Colored-Nether-Portals)](LICENSE)

Any color for your Nether portals. Sneak and right-click a portal with an empty
hand to open a color menu: solid colors from the 16 dyes or any custom color,
gradients through up to 16 colors, rainbows, color cycles and pulses, with a
live preview. Or just right-click a portal with a dye. Gradients fade smoothly
across the whole portal, the floating particles and the swirl over your screen
while you travel take the portal's color too.

On a server with the mod, colors are saved with the world and every player with
the mod sees them. On a server without it, they are kept on your computer and
only you see them. Vanilla players see the usual purple.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`), the
shared MyCelium library. Minecraft 26.3, Fabric + Forge + NeoForge, Java 25.

Docs: https://wiki.mycelium-gg.org/colorednetherportals/

## Config

`config/colorednetherportals.json`, editable live with `/mycel set colorednetherportals <key> <value>`:

| Key | Default | Side | Meaning |
|---|---|---|---|
| `enabled` | `true` | both | Whether the mod does anything at all |
| `allowAnimated` | `true` | server | Allow rainbow, color cycle and pulse portals |
| `dyeColoring` | `true` | server | Right-clicking a portal with a dye colors it |
| `consumeDye` | `true` | server | Coloring with a dye uses it up (not in creative) |
| `debug` | `false` | both | Log color changes and syncing |

## How it works

The portal model has two layers on one plane: vanilla's texture and a grey
swirl split into 4x4 cells. Block tints show exactly one layer per block, each
cell in its own color. The swirl (`scripts/make_portal_texture.py`) is drawn
from math, not copied from Minecraft. Particles are colored by wrapping the
portal particle provider; the screen effect by rewriting the frames of the
model's particle texture. No mixins.

## Building

`./gradlew build`; `./gradlew :fabric:runClient -Pworld="<save>"` opens a world
directly. Mycel comes from [JitPack](https://jitpack.io/#MyCelium-GG/MyCel-Lib)
(`com.github.MyCelium-GG:MyCel-Lib:1.4.0`).

## Releasing

Push a version tag (`git tag 1.0.0 && git push origin 1.0.0`). The `release`
workflow builds the jars, attaches them to a GitHub Release, uploads them to
CurseForge once `curseforge_project_id` is set in `gradle.properties` (token
in the `CURSEFORGE_TOKEN` secret) and announces it on Discord (`DISCORD_WEBHOOK`).

## License

MIT, see [LICENSE](LICENSE).
