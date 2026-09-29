# War of Dots: Frontline — Android RTS project

## Included in this version
- Android landscape real-time strategy prototype with touch controls.
- Infantry, tanks, light air wings, fighters, bombers, and transports.
- Unit selection, drag-box squad selection, move/attack/defend/retreat command modes.
- HQ health, combat, neutral capture points, income, unit production.
- Placeable barracks, factories, airfields, and defensive turrets; power building gives bonus income.
- Aircraft fuel, low-fuel return-to-base behavior, and refueling near HQ.
- Fog of war, scouting vision, radar building vision.
- Smart and Divinely Smart AI modes, including different production and objective behavior.
- Skirmish and mission menu, pause/restart, win/loss states.
- GitHub Actions workflow that builds and uploads a debug APK.

## Build APK
1. Upload the project contents to a GitHub repository. Keep `.github/workflows/build.yml` in that exact path.
2. Push to `main`/`master`, or open **Actions** and run **Build War of Dots Frontline APK**.
3. Download the `WarOfDots-Frontline-APK` artifact from the successful workflow run.
4. Extract the artifact ZIP and install `app-debug.apk` on Android (allow installation from that source if prompted).

This is a substantial prototype, not a commercial release. Campaign has two objective variants plus HQ destruction, and the AI is rule-based rather than a trained or omniscient system. Multiplayer, saved progression, polished audio/animation, and device testing remain future work. The APK has not been compiled or device-tested in this environment.
