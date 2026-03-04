# 🌿 Forest_Run

A high-fidelity, colorful 2D endless runner for Android. A spiritual successor to the Chrome Dino game, reimagined as a lush, living **"Cottagecore" forest** with deep parallax, reactive nature effects, and behavioural animal AI.

> *"Not just an obstacle course — a living forest you run through."*

---

## ✨ Features

- **Juicy Gameplay** — Every input triggers satisfying visual + haptic + audio feedback
- **Living Forest** — Wind sway, falling petals, fireflies, and a full day/night cycle
- **Behavioural AI** — 19 unique entities, each with distinct AI (Fox mirrors your jumps; Wolf charges mid-screen; Dog emits bark projectiles)
- **Biome System** — 6 distinct biomes cycle every 500m with unique obstacle pools and visuals
- **Bloom State** — Collect 10 Seeds for a 5-second invincibility power-up that transforms the world
- **Garden Meta-Loop** — Seeds grow permanent plants in your personal Garden on the main screen
- **48-Frame Run Cycle** — Fluid pixel-art character animations with squash & stretch physics
- **Adaptive Music** — Tempo syncs with run speed; orchestral swell during Bloom

---

## 📁 Documentation

| Document | Description |
|---|---|
| [GDD.md](docs/GDD.md) | Full Game Design Document — vision, flow, mechanics, scoring |
| [ENTITY_DATABASE.md](docs/ENTITY_DATABASE.md) | All 19 entities — AI, hitboxes, behaviours, biome affinity |
| [VISUAL_FX_SPEC.md](docs/VISUAL_FX_SPEC.md) | Parallax, particles, wind sway, lighting, camera effects |
| [TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md) | Kotlin class design, engine, state machines, spawner |
| [ANDROID_SETUP.md](docs/ANDROID_SETUP.md) | Manifest, build.gradle, asset naming, setup checklist |

---

## 🌿 Entity Overview

### Ground Flora (5)
`Lily of the Valley` · `Hyacinth` · `Eucalyptus` · `Vanilla Orchid` · `Cactus`

### Trees (4)
`Weeping Willow` · `Jacaranda` · `Bamboo` · `Cherry Blossom`

### Birds (5)
`Owl` · `Duck` · `Eagle` · `Tits` · `Chickadees`

### Animals (5)
`Wolf` · `Cat` · `Fox` · `Hedgehog` · `Dog`

---

## 🎮 Controls

| Input | Action |
|---|---|
| Tap | Standard jump |
| Hold | High jump (height = hold duration) |
| Swipe Down | Duck / Slide |

---

## 🏗️ Tech Stack

- **Language:** Kotlin
- **Platform:** Android (API 24+)
- **Rendering:** SurfaceView custom game loop @ 60 FPS
- **Package:** `com.yourname.forest_run`
- **Orientation:** sensorLandscape

---

## 📂 Project Structure

```
app/src/main/
├── java/com/yourname/forest_run/
│   ├── engine/          ← GameView, GameThread, GameStateManager
│   ├── entities/        ← All 19 entity classes
│   │   ├── flora/
│   │   ├── trees/
│   │   ├── birds/
│   │   └── animals/
│   ├── systems/         ← EntityManager, BiomeManager, ParticleManager
│   ├── ui/              ← HUD, GameOverScreen, GardenScreen
│   └── utils/           ← SpriteSheetHelper, SaveManager, MathUtils
└── assets/              ← Pixel art .png sprite files
```

---

## 🚀 Getting Started

1. Open project in **Android Studio Hedgehog** or later.
2. Place sprite assets in `app/src/main/assets/` per naming convention in [ANDROID_SETUP.md](docs/ANDROID_SETUP.md).
3. Run on a physical device (recommended) or emulator with landscape orientation.
4. Follow the prompting guide in [TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md) to generate entity code.

---

## 📝 License

Personal project — not for commercial distribution.

---

*Project: Forest_Run | Platform: Android | Language: Kotlin*
