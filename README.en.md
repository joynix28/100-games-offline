# 100 Games Offline

**A collection of mini-games. Free, no ads, fully offline.**

One APK, one list: open it, pick a game, play. No account, no virtual currency,
no ad between two rounds.

[Français](README.md) · [Download the APK](https://github.com/joynix28/100-games-offline/releases/latest)

## Games (16, 48 variants)

Tic-tac-toe/gomoku, Connect 4, Minesweeper, 2048, Sliding puzzle, Memory, Snake,
Pong, Breakout, Simon, Mental math, Hangman, Guess the number, Matches,
Rock-paper-scissors, Checkers.

Each game has several variants (board size, difficulty, speed). The collection
is built to grow: one file per game, one entry in `Catalog.kt`.

Settings: French/English, interface size, sound, vibration, records.

## Build

```bash
./gradlew :app:assembleRelease
./gradlew :desktop:run
```

## Licence

Apache 2.0. See `LICENSE` and `NOTICE`.

> If you reuse this code, its design or its idea, **credit Joynix** with a link
> back to this repository.
