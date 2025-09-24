# Puff Counter (Android)

A privacy-first puff counter with:
- One-tap floating overlay bubble
- Offline storage (Room)
- Auto sessions (no manual save needed)
- Custom day rollover (default 4 AM)
- Import/Export CSV (compatible with earlier web app)

## Build
- Android Studio Ladybug or newer
- Gradle wrapper included
- Open the project, Sync Gradle, Run

## Permissions
- **Overlay** (to show the floating bubble): grant from system settings when prompted.

## Settings
- Session timeout: 12 minutes (code constant)
- Day rollover: 4 AM (see `util/DayRollover.kt`)

## Import/Export
- Buttons at the bottom of the main screen
- Import accepts ISO-8601 timestamps from the web app export
- After import, sessions are rebuilt automatically

## License
MIT
