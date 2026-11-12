# Celestial Client — Fabric 26.3

A premium pink-accented glassmorphism utility client for **Minecraft 26.3**
(Java Edition). ESC opens the Celestial interface (replaces the vanilla pause
menu); 9 modules; live themes; profiles; FPS/Ping HUD.

Every Mojang-mapping name used here was verified against the real 26.3
`client.jar` (MojMap, no Yarn): `Minecraft.player/level/options/font`,
`ClientTickEvents`, `KeyMappingHelper`, `HudElementRegistry`,
`GuiGraphicsExtractor.text/fill`, `Screen`, `AbstractWidget`,
`EditBox`, `OptionInstance gamma/autoJump`, `Abilities`,
`ModelBlockRenderer.shouldRenderFace` (XRay),
`LevelExtractor.allChanged` (chunk refresh, same as vanilla F3+A),
`Minecraft.pauseGame` (ESC hook), and more.

## Requirements

- **JDK 25** (build + run). Gradle itself is included via `gradlew` / `gradlew.bat`, so you don't need to install Gradle.
- Internet on first build (downloads Minecraft 26.3 + mappings + Gradle 9.8.2)

## Build

1. Copy this whole `celestial` folder to your computer (e.g. Desktop), keeping the structure. Do NOT put it in `mods/`.
2. In a terminal inside the folder:
   - Windows: `gradlew.bat build`
   - Mac/Linux: `./gradlew build`
3. The mod jar is at `build/libs/celestial-1.0.0.jar`.

## Install in Minecraft

1. Install **Fabric Loader 0.20.1** for Minecraft **26.3**.
2. Put **Fabric API `0.165.2+26.3`** (or newer `+26.3`) into the `mods` folder.
3. Put `celestial-1.0.0.jar` into `mods` too.
4. Launch the `fabric-loader-0.20.1-26.3` profile with Java 25.

## Use

- **ESC** in a world opens Celestial (replaces the pause menu; ESC again closes).
  Singleplayer still pauses, multiplayer keeps running.
- Extra GUI hotkey: unbound by default, set it in Options > Controls > Celestial.
- Modules (all rebindable in Controls): XRay (X), FullBright (P),
  EntityESP (J), AntiAFK (K), Fly (G), Speed (V), Sprint (R), NoFall (B),
  Step (M).
- Settings, themes, and profiles apply instantly, no restart needed.
- Config lives in `config/celestial/` (settings + `profiles/*.json`).

## Notes

- Tested against mappings of release **26.3** only.
- Please don't use this to ruin other people's multiplayer experience —
  many servers detect and ban this kind of mod.
