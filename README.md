# projectAI

# AURA Agent

Native Android AI-agent-style assistant starter built with Kotlin + Jetpack Compose.

## Included now

- Premium dark/light home UI
- Animated AI orb and status
- Text command input
- Voice input using Android speech recognition
- Quick actions for Files, Settings, Camera, Downloads
- Recent command history
- Microphone permission handling

## Next agent modules

- Installed-app discovery/search and app launching
- File search by name/type and recent files
- Create/rename/copy/move/share/delete with confirmation
- Permission Center
- Natural-language command router
- Optional local AI layer

The app intentionally does not bypass Android security or silently control other apps.

## AI modes

AURA chat supports two modes:

- Offline: uses the local command and conversation engine.
- Online: uses an OpenAI-compatible chat-completions endpoint when configured, then falls back to Offline if the request fails.

Online mode requires these values in the app's `aura_preferences` preferences:

- `online_ai_api_key`
- `online_ai_endpoint` (optional; defaults to the OpenAI-compatible endpoint)
- `online_ai_model` (optional; defaults to `gpt-4o-mini`)

The API key is intentionally not hardcoded in the source or Gradle files.
