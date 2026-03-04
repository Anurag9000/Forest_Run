# Forest_Run — Android Studio Setup Guide

Complete reference for setting up the Android project, configuring the manifest, organizing assets, and verifying the build before adding game code.

---

## 1. Package & Project Setup

| Setting | Value |
|---|---|
| **Package Name** | `com.yourname.forest_run` |
| **Project Name** | `Forest_Run` |
| **Min SDK** | API 24 (Android 7.0 Nougat) |
| **Target SDK** | API 34 (Android 14) |
| **Language** | Kotlin |
| **Build System** | Gradle (Kotlin DSL or Groovy) |
| **Screen Orientation** | Landscape / sensorLandscape |

---

## 2. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourname.forest_run">

    <!-- Haptic feedback permission -->
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Optional: Keep screen on during gameplay -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ForestRun">

        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:immersive="true"
            android:keepScreenOn="true">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

### Key Manifest Settings Explained

| Setting | Purpose |
|---|---|
| `screenOrientation="sensorLandscape"` | Locks to landscape, auto-flips for device orientation |
| `configChanges` | Prevents Activity recreation on orientation change |
| `immersive="true"` | Hides system UI for full-screen gameplay |
| `keepScreenOn="true"` | Prevents screen from turning off during gameplay |
| `VIBRATE` permission | Required for haptic feedback on jumps/collisions |

---

## 3. MainActivity.kt

```kotlin
package com.yourname.forest_run

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen — hide system bars
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Hide navigation bar
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }
}
```

---

## 4. build.gradle (app module)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourname.forest_run"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourname.forest_run"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.code.gson:gson:2.10.1")  // For save system JSON
}
```

---

## 5. Asset Folder Structure

Create the `assets` folder at: `app/src/main/assets/`

In Android Studio: right-click `main` → New → Folder → Assets Folder.

### Required Sprite Files

All sprite files are `.png` format, placed in `app/src/main/assets/`:

#### Player

| Filename | Description | Frames |
|---|---|---|
| `player_run.png` | 48-frame horizontal sprite sheet | 48 |
| `player_jump.png` | Jump arc animation | 12 |
| `player_duck.png` | Ducking/sliding | 8 |
| `player_land.png` | Landing impact | 4 |
| `player_rest.png` | Sitting (game over) | 24 |
| `player_bloom.png` | Bloom state (glowing) | 16 |

#### Ground Flora

| Filename | Description |
|---|---|
| `flora_cactus.png` | Single cactus, static |
| `flora_lily_of_valley.png` | Lily cluster, idle + bloom states |
| `flora_lily_of_valley_glow.png` | Night glow overlay |
| `flora_hyacinth.png` | Hyacinth spike, single |
| `flora_eucalyptus.png` | Tall slanted stalk |
| `flora_vanilla.png` | Vine with overhead branch |

#### Trees

| Filename | Description |
|---|---|
| `tree_weeping_willow.png` | Full tree with drooping leaves |
| `tree_jacaranda.png` | Tree with purple canopy |
| `tree_bamboo.png` | Single bamboo stalk |
| `tree_cherry_blossom.png` | Branch section with blossoms |

#### Birds

| Filename | Description | Frames |
|---|---|---|
| `bird_owl_idle.png` | Perched owl, eyes open | 4 |
| `bird_owl_dive.png` | Owl diving | 8 |
| `bird_duck_fly.png` | Duck flying | 8 |
| `bird_eagle_fly.png` | Eagle soaring | 8 |
| `bird_eagle_dive.png` | Eagle dive | 6 |
| `bird_tit_fly.png` | Blue tit flapping | 6 |
| `bird_chickadee_fly.png` | Chickadee flapping | 6 |

#### Animals

| Filename | Description | Frames |
|---|---|---|
| `animal_wolf_walk.png` | Wolf slow walk | 8 |
| `animal_wolf_run.png` | Wolf full sprint | 8 |
| `animal_wolf_howl.png` | Howl animation | 4 |
| `animal_cat_sit.png` | Sitting cat, tail flick | 4 |
| `animal_fox_trot.png` | Fox approach trot | 8 |
| `animal_fox_jump.png` | Fox jump (mimic) | 8 |
| `animal_hedgehog_run.png` | Hedgehog scurrying | 6 |
| `animal_hedgehog_curl.png` | Hedgehog curled on hit | 4 |
| `animal_dog_sit.png` | Dog sitting/standing | 4 |
| `animal_dog_bark.png` | Dog bark pose | 4 |

#### Particles & FX

| Filename | Description |
|---|---|
| `particle_petal_pink.png` | Cherry Blossom petal |
| `particle_petal_purple.png` | Jacaranda petal |
| `particle_dust.png` | Landing dust puff |
| `particle_seed_orb.png` | Collectible seed glow |
| `particle_bark_wave.png` | Dog bark shockwave ring |
| `particle_sparkle.png` | Generic sparkle |
| `particle_firefly.png` | Firefly glow dot |

#### UI

| Filename | Description |
|---|---|
| `ui_bloom_meter_bg.png` | Bloom meter background bar |
| `ui_bloom_meter_fill.png` | Bloom meter fill |
| `ui_seed_icon.png` | Seed count icon |

#### Backgrounds (Parallax Layers)

| Filename | Description |
|---|---|
| `bg_layer1_mountains.png` | Distant mountains/sky — loops |
| `bg_layer2_trees_home.png` | Mid trees — Home Grove |
| `bg_layer2_trees_spring.png` | Mid trees — Spring Orchard |
| `bg_layer2_trees_ancient.png` | Mid trees — Ancient Grove |
| `bg_layer2_trees_violet.png` | Mid trees — Violet Path |
| `bg_layer3_ground.png` | Main ground path |
| `bg_layer4_foreground.png` | Close-up blurred grass strip |

---

## 6. Audio Files

Place all audio files in `app/src/main/res/raw/`:

| Filename | Description |
|---|---|
| `music_garden.ogg` | Menu/Garden ambient music |
| `music_run_layer1.ogg` | Running music — drum beat only |
| `music_run_layer2.ogg` | Mid-game layer — bass + flute |
| `music_run_layer3.ogg` | Late-game full track |
| `music_bloom.ogg` | Orchestral Bloom State swell |
| `music_rest.ogg` | Calm game-over outro |
| `sfx_jump.ogg` | Player jump whoosh |
| `sfx_land.ogg` | Landing thud + grass |
| `sfx_seed.ogg` | Seed pickup ping |
| `sfx_bloom_start.ogg` | Bloom activation chime |
| `sfx_dog_bark.ogg` | Dog bark |
| `sfx_eagle_screech.ogg` | Eagle screech dive cue |
| `sfx_wolf_howl.ogg` | Wolf charge howl |
| `sfx_kindness_bonus.ogg` | Cat kindness sparkle |
| `sfx_game_over.ogg` | Rest state transition |

---

## 7. Theme & Styles (No UI Bars)

In `res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.ForestRun" parent="Theme.AppCompat.NoActionBar">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

---

## 8. Loading Assets in Code

```kotlin
// Load bitmap from assets folder
fun loadBitmap(context: Context, filename: String): Bitmap {
    return context.assets.open(filename).use { stream ->
        BitmapFactory.decodeStream(stream)
    }
}

// Load and scale a bitmap
fun loadScaledBitmap(context: Context, filename: String, width: Int, height: Int): Bitmap {
    val raw = loadBitmap(context, filename)
    return Bitmap.createScaledBitmap(raw, width, height, true)
}
```

---

## 9. Performance Checklist

Before testing on device, verify these are in place:

- [ ] `deltaTime` used for ALL movement — never hardcoded speeds.
- [ ] Bitmaps pre-loaded once (in `init`) — never decoded inside `draw()`.
- [ ] `Paint` objects created once and reused — never instantiated in `draw()`.
- [ ] Entity pool / recycled objects — avoid GC during gameplay.
- [ ] `Canvas.save()` / `Canvas.restore()` used correctly for transformations.
- [ ] `SurfaceView` used for game view — not a regular `View`.
- [ ] Parallax layers are looping seamlessly (no visible seam).
- [ ] Particle count capped (max 200 active particles at once).
- [ ] Haptic calls are fire-and-forget (non-blocking).
- [ ] Audio assets are `.ogg` format (best Android performance).

---

## 10. Debugging Physics Checklist

Run these manual tests once the basic engine is working:

| Test | Expected Result |
|---|---|
| Short tap on flat ground | Character performs a low hop |
| Long press on flat ground | Character performs maximum height jump |
| Swipe down during run | Character immediately ducks/slides |
| Swipe down during jump | Character falls faster (fast-fall) |
| Run into Cactus | REST state triggered, Screen shake |
| Jump over Cat perfectly | Kindness Bonus sparkles and ×2 score |
| Jump while Fox is in trigger zone | Fox also jumps simultaneously |
| Leave Wolf to cross halfway | Wolf howls and doubles speed |
| Touch Hedgehog | Speed reduces 50%, no immediate death |
| Score hits 1000 | Subtle screen shake, no other change |
| Collect 10 Seeds | Bloom State activates |
| Bloom State ends after 5s | Normal gameplay resumes |
| Reach 500m | BiomeTransition fires, background shifts |

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
