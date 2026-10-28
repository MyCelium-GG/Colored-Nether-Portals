# Changelog

All notable changes to Colored Nether Portals are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
It runs on Minecraft 26.3, 26.2 and 26.1 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.0.0] - Unreleased

### Added

- Runs on Minecraft 26.3, 26.2 and 26.1 (26.1, 26.1.1, 26.1.2): one set of
  Fabric, Forge and NeoForge jars per Minecraft line. Requires Mycel 1.5.1.
- Sneak and right-click a Nether portal with an empty hand to open the color
  menu: Vanilla, Solid, Gradient, Rainbow, Color cycle and Pulse, with slow,
  normal and fast speeds for the animated ones, and a live preview of the portal.
- Gradients and pulses take 2 to 16 colors; 16 dye colors plus 8 extras, a
  color picker and a hex field.
- Right-click a portal with a dye to color it, also for players without the mod.
- Smooth gradients across the whole portal, colored portal particles, and the
  screen swirl and loading screen take the portal's color while you travel.
- Servers with the mod save colors with the world and show them to every player
  with the mod; on servers without it colors stay on your computer.
- Config in `config/colorednetherportals.json` (`enabled`, `allowAnimated`,
  `dyeColoring`, `consumeDye`, `debug`).
- Built on Mycel 1.4.0.
