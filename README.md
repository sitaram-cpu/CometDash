# Comet Dash

A small arcade game for the Android final project: drag your ship to dodge
falling comets, tap falling stars to pop them for bonus points, and survive
as long as you can. Built in Kotlin with a single custom `View` as the game
engine.

## What the game is

- **Objective / end condition:** you start with 3 lives. Getting hit by a
  comet costs a life (with a brief invincibility flash so one comet can't
  wipe you out instantly). The game ends when lives reach 0, and your final
  score is shown.
- **Controls:** drag anywhere on the play area to steer the ship left/right.
  Tap directly on a falling star to pop it for bonus points instead of
  catching it with the ship.
- **Secret:** tap the score display 5 times quickly to raise a temporary
  shield. (Don't tell the class... or do, it's a feature.)

## Opening the project

1. Open **Android Studio** (Koala/2024.1 or newer recommended).
2. **File → Open...** and select the `CometDash` folder (the one containing
   `settings.gradle.kts`).
3. Android Studio will say the Gradle wrapper is missing and offer to
   generate one — click **OK**. (The wrapper's binary jar isn't included in
   this handoff; Android Studio recreates it automatically on first open.)
4. Let Gradle sync. It will download Android Gradle Plugin 8.5.0, Gradle
   8.7, and a handful of AndroidX/Material libraries the first time — this
   needs an internet connection and can take a few minutes.
5. Run the `app` configuration on an emulator or a physical device
   (**minSdk 26**, Android 8.0+ — this covers essentially any classroom
   Android device from the last several years).

If Gradle complains about a missing JDK, point it at JDK 17 in
**Settings → Build Tools → Gradle → Gradle JDK** (Android Studio usually
bundles one already).

## Project structure

```
app/src/main/java/com/sitaram/cometdash/
  GameEntities.kt      - Ship/FallingEntity/PopEffect data classes + tunable GameConfig constants
  GameView.kt           - the game itself: custom View, game loop, drawing, touch, animations
  GameViewModel.kt       - score/lives/elapsed-time holder that survives rotation
  MainActivity.kt        - app bar, HUD, dialogs, pause/resume, easter egg, share intent
  HighScoresActivity.kt   - second screen: the high-score board
  HighScoreStore.kt      - SharedPreferences-backed persistence (the "database")
  HighScore.kt           - one score entry
  HighScoreAdapter.kt     - RecyclerView adapter for the high-score list
app/src/main/res/        - layouts, strings (English + Spanish), colors, the app icon, menu
```

## Challenge checklist (13 of 18 — only 8 are needed for an A)

| # | Challenge | Where it lives |
|---|---|---|
| 1 | Custom `View` overriding `onDraw`/`onTouchEvent` | `GameView.kt` — the whole class |
| 2 | One animation | `MainActivity.onGameOver()` — the "Game Over" text's scale/alpha entrance |
| 3 | A second animation combined via `AnimatorSet` | `GameView.popStar()` — ring-scale + fade combined with `AnimatorSet.playTogether()` |
| 4 | Animation reacts to touch | Same `popStar()` — only runs when the player taps a star |
| 5 | Dynamic text | Score/lives TextViews update live; the pause menu item's icon/title flips between Pause/Resume |
| 6 | App bar with a button | `activity_main.xml` Toolbar + `main_menu.xml` (Pause, High Scores) |
| 7 | Restart without closing the app | "Play Again" button in the game-over dialog → `GameView.startNewGame()` |
| 8 | Handle screen rotation sensibly | `GameViewModel.kt` + `MainActivity.onPause()/onCreate()` — score/lives/elapsed time survive rotation; only the exact on-screen comet/star positions reset |
| 9 | Second screen, data both ways | `HighScoresActivity` — receives the latest score via Intent extras; returns whether the board was cleared via an activity result |
| 10 | Array/database display that updates with interaction | High-score list (`HighScoreStore` + `HighScoreAdapter`) re-reads and re-renders after every save/clear |
| 11 | Text entry that affects the app | Player initials entered in `dialog_enter_name.xml`, saved into the high-score board |
| 12 | Easter egg / cheat | Tap the score label 5× within 2.5s → shield (`MainActivity.handleScoreTap()`) |
| 13 | Implicit intent | `MainActivity.shareScore()` — `ACTION_SEND` share chooser |

Also included but not counted above (free bonuses): a custom adaptive app
icon (`drawable/ic_launcher_*.xml`), a Spanish localization
(`values-es/strings.xml`), and the whole play field is hand-drawn with
shapes/paths (ship, comets, twinkling starfield) rather than images.

If you want to push past 13, the two lowest-effort additions left are a
camera intent (e.g. snap a photo for a player avatar shown in the corner of
the HUD) and TalkBack support (the ship already has a content description —
you'd add an accessible "move left / move right" button pair as an
alternate control scheme).

## Before you submit — test this yourself

This project was written and reviewed carefully, but it was **not** compiled
inside an Android build environment (no Android SDK was available where it
was written), so please actually build and play it before submitting. A
short checklist:

- [ ] App launches without crashing and the instructions banner appears, then fades
- [ ] Dragging moves the ship; tapping a star pops it and adds score
- [ ] Getting hit by a comet 3 times ends the game and shows the Game Over text + dialog
- [ ] "Play Again" actually restarts
- [ ] Rotate the phone mid-game — score/lives should NOT reset
- [ ] Pause/Resume button in the toolbar works and the icon/label swap
- [ ] High Scores screen opens, shows your score, and the name-entry dialog appears for a qualifying score
- [ ] Clearing scores works and you see a "cleared" toast back on the main screen
- [ ] Share Score opens the Android share sheet
- [ ] Tapping the score label 5× fast shows "Shield Activated"
- [ ] Switch the device language to Spanish and confirm the UI text changes

If anything misbehaves, the most likely culprits are tuning numbers in
`GameConfig` (speeds/spawn rates) rather than structural bugs — those are
safe to tweak.

## Pushing to GitHub

You said you already have a repo ready, so from inside this folder:

```bash
git init
git add .
git commit -m "Comet Dash - final project"
git branch -M main          # or whatever branch name you want graded
git remote add origin <your-repo-url>
git push -u origin main
```

Remember the submission rules from the assignment: submit the link to the
**specific branch** you want graded (not just the repo's default branch if
your work lives elsewhere), and don't commit anything after you submit the
link.
