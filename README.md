# Phone Mod (Forge 1.20.1 + Plasmo Voice)

Craftable phones with a Call app and a Messages app. Calls are carried over Plasmo Voice.

## Phone numbers

- Every player gets a random, unique **10 digit number** the first time they join. It is saved with the world.
- Your number is shown on the phone home screen. Give it to friends.

## How it plays

- Craft a **Phone** (iron / lapis / glass pane / redstone, see `data/phonemod/recipes/phone.json`) or take it from the creative tab.
- **Right-click the phone** (or press **P**, rebindable) to open it.

### Call app
- **Add contact**: type a name and the other player's 10 digit number.
- You can **only call numbers that are in your contacts**.
- The other player gets a ring sound, a chat message with **[Answer] [Decline]** buttons, and the same options on their phone screen.
- Once answered, **just talk with your normal Plasmo Voice mic key**. The other player hears you at any distance, on a separate "Phone" volume slider (Plasmo Voice menu, Volume tab). People standing near you still hear what you say in person, because proximity voice is never cancelled.
- Commands (same actions as the GUI): `/phone call <number>`, `/phone answer`, `/phone decline`, `/phone hangup`.

### Messages app
- **New chat**: type a player's number and start chatting.
- **New group**: give it a name and a list of numbers (comma separated). You are added automatically.
- Inside a group, **Add member** adds another person by number.
- Messages are saved with the world (last 200 per chat).

Config: `config/phonemod-common.toml` (ring timeout, whether a phone item is required to call/receive/open, hang up if the phone is lost).

## Build

You need JDK 17 and the Gradle wrapper from the official Forge 1.20.1 MDK
(copy `gradlew`, `gradlew.bat` and `gradle/` next to `build.gradle`; ForgeGradle 6 wants Gradle 8.1 to 8.8).

```
./gradlew build
```

The jar lands in `build/libs/`. Install it on the **server and every client**, alongside **Plasmo Voice 2.1.17 (Forge)**.

## Plasmo Voice version

`gradle.properties` pins `plasmovoice_version=2.1.17`. If you run a different
Plasmo Voice release, change `plasmovoice_version` and `plasmovoice_version_range` to match.

## How the voice part works (`voice/PhoneVoiceAddon.java`)

- `@Addon` class loaded from the mod constructor via `PlasmoVoiceServer.getAddonsLoader().load(...)`.
- Registers a `phone` source line (own icon, own volume slider).
- When a call is answered, one direct source per participant is created (a direct source is heard only by the player it is attached to).
- `PlayerSpeakEvent` / `PlayerSpeakEndEvent` are observed at LOWEST priority, and each frame from a participant is forwarded unchanged to the other participant's direct source.

## Code layout

- `PhoneData` - numbers, contacts, chats (world save data)
- `PhoneService` - contacts, messages, group chats, sends screen data to the client
- `call/CallManager` - ringing, answer, decline, hang up, timeouts
- `client/PhoneScreen` - the phone GUI
- `net/PhoneNet` - packets

## Known limits

- If both callers are physically standing next to each other, the listener hears both the in-person voice and the phone. Hang up or step away.
- Calls need both players to be online and connected to Plasmo Voice.
- A number only exists once that player has joined the world with this mod installed.
