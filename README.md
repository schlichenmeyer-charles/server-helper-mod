# Server Helper Mod

**Server Helper Mod** is a lightweight, server-side Forge mod for **Minecraft 1.20.1** that helps with common server administration tasks: scheduled restart warnings, configurable server rules, and banned-item enforcement.

The mod is intended for dedicated servers and does not require players to install anything on their clients.

---

## What's New in 1.10.0

- Optional FTB Chunks integration that removes force-loading from teams with no login activity for 7 days by default.
- Automatic checks at server startup and on a configurable interval.
- `/serverhelper ftbchunks unloadall` to remove every FTB Chunks force-load while preserving claims.
- No hard dependency: the rest of Server Helper works normally without FTB Chunks, FTB Teams, or FTB Library.

---

## Features

- **Scheduled restarts**
  - Define one or more daily restart times in server local time.
  - Broadcast configurable warning messages before the restart.
  - Optionally run a server command when the countdown reaches zero.

- **Rules command**
  - Players can run `/rules` to view configured server rules.
  - Optional Discord and website links are shown as clickable chat links.

- **AFK status**
  - Players can run `/afk` to announce that they are away.
  - AFK players are marked with `[AFK]` in the tab menu.
  - Players are automatically marked AFK after 10 minutes without activity.
  - Moving, chatting, interacting with an entity, or running `/afk` again clears AFK status.

- **Staff presence tools**
  - Players can run `/staff` to see online staff members.
  - Staff can run `/seen <player>` to view last-known player activity and location.
  - Staff can run `/vanish` to hide from non-staff tab lists and gain no-particle invisibility.
  - Staff can run `/enchant <enchantment>` and `/repair` on their held item.
  - Staff membership is controlled by the Forge permission node `server_helper_mod.staff`, with vanilla operators allowed by default.

- **Banned item management**
  - Add, remove, reload, and list banned items in-game.
  - Soft bans block placement/deployment behavior and remove soft-banned items from automation containers such as dispensers and droppers.
  - Hard bans block use and remove matching item stacks from player inventories, ender chests, open containers, and loaded containers.

- **Admin utilities**
  - Reload all mod data without restarting the server.
  - Check the next scheduled restart.
  - Send test restart warnings.
  - Check the server's local time.
  - Toggle maintenance mode for non-operator players.
  - View server and dimension performance snapshots.
  - Register configurable top-level command aliases.

- **Optional FTB Chunks cleanup**
  - Keeps FTB chunk claims intact while removing force-loading from inactive teams.
  - Uses FTB Chunks' team-level last-login data.
  - Runs at server startup and on a configurable interval.
  - Remains inactive when the required FTB mods are not installed.

---

## Commands

### Player Commands

| Command | Description |
| --- | --- |
| `/rules` | Shows the configured server rules and helpful links. |
| `/afk` | Toggles your AFK status and broadcasts the change to online players. |
| `/staff` | Lists online staff members. Vanished staff are hidden from non-staff players. |
| `/discord` | Default alias for `/rules`; configurable in `aliases.commands`. |
| `/website` | Default alias for `/rules`; configurable in `aliases.commands`. |
| `/banitems list` | Lists currently banned items. |

### Operator Commands

| Command | Description |
| --- | --- |
| `/serverhelper reload` | Reloads config, rules, restart schedule, banned items, and sweeps loaded inventories/containers. |
| `/serverhelper maintenance status` | Shows whether maintenance mode is enabled. |
| `/serverhelper maintenance on` | Enables maintenance mode and disconnects non-operators after login. |
| `/serverhelper maintenance off` | Disables maintenance mode. |
| `/serverhelper status` | Shows server TPS/MSPT plus per-dimension entity, player, chunk, and MSPT snapshots. |
| `/serverhelper ftbchunks status` | Shows whether the optional integration is active and how many chunks are force-loaded. |
| `/serverhelper ftbchunks unloadinactive` | Immediately runs the configured inactive-team cleanup. |
| `/serverhelper ftbchunks unloadall` | Removes force-loading from every FTB Chunks chunk on the server while preserving claims. |
| `/serverhelper testwarn <minutes>` | Broadcasts a test restart warning. |
| `/serverhelper restartstatus` | Shows the next scheduled restart and remaining seconds. |
| `/serverhelper getlocaltime` | Shows the server's local date/time. |
| `/seen <player>` | Shows whether a player is online or when and where they were last seen. Requires `server_helper_mod.staff` or operator permissions. |
| `/vanish` | Toggles vanish for the executing staff member. Requires `server_helper_mod.staff` or operator permissions. |
| `/enchant <enchantment>` | Applies the requested enchantment at its maximum level to the item in your main hand. Requires `server_helper_mod.staff` or operator permissions. |
| `/repair` | Fully repairs the item in your main hand. Requires `server_helper_mod.staff` or operator permissions. |
| `/banitems reload` | Reloads the banned item file and sweeps loaded inventories/containers. |
| `/banitems hardban hand` | Hard-bans the item currently held in your main hand. |
| `/banitems hardban <item>` | Hard-bans an item by registry ID, such as `minecraft:bedrock`. |
| `/banitems softban hand` | Soft-bans the item currently held in your main hand. |
| `/banitems softban <item>` | Soft-bans an item by registry ID. |
| `/banitems unban hand` | Removes the ban for the item currently held in your main hand. |
| `/banitems unban <item>` | Removes a ban by registry ID. |

`/serverhelper *`, `/seen`, `/vanish`, `/enchant`, and `/repair` require `server_helper_mod.staff` or vanilla operator permissions.

---

## Configuration

After the server runs once, Forge generates the common config file at:

```text
config/server_helper_mod-common.toml
```

Banned items are stored separately at:

```text
config/server_helper_mod_banned_items.json
```

Seen player data is stored separately at:

```text
config/server_helper_mod_seen_players.json
```

### Example Config

```toml
[general]
	# Turn on and off automatic restart messages.
	enable_messages = true

[restart]
	# Daily restart times (24h HH:mm) in server local time. e.g. ["04:00","16:00"]
	times = ["04:00"]

	# Send warnings when restart is N minutes away.
	warn_minutes = [30, 10, 5, 1]

	# Server command to execute at the end of the timer (no leading '/').
	# Example: "stop" or "restart"
	command = "stop"

	# Whether the server should run the above command when the countdown reaches zero.
	execute_at_zero = false

[rules]
	# Rules shown when a player runs /rules
	rules = [
		"Be respectful to other players.",
		"No griefing, stealing, or unauthorized base raiding.",
		"No profane, sexual, or adult-themed chat or builds.",
		"No cheating, hacked clients, exploits, or duping.",
		"Follow admin instructions. Punishment is at admin discretion."
	]

	# Discord invite URL shown in /rules
	discord_url = "https://discord.gg/yourserver"

	# Website URL shown in /rules
	website_url = "https://yourserver.com"

[maintenance]
	# When true, non-operator players are disconnected after login.
	enabled = false

	# Disconnect message shown to non-operator players while maintenance mode is enabled.
	message = "The server is currently under maintenance. Please try again later."

[aliases]
	# Command aliases in alias=target format. Do not include leading slashes.
	# Example: discord=rules
	commands = ["discord=rules", "website=rules"]

[ftb_chunks]
	# Remove force-loading from FTB teams that have been inactive for the configured number of days.
	# Ignored when FTB Chunks/Teams/Library are not installed.
	unload_inactive_enabled = true

	# Real-world days since the owning team last had a member log in.
	inactive_days = 7

	# Minutes between automatic checks after startup.
	check_interval_minutes = 60
```

### Config Options

| Option | Description |
| --- | --- |
| `general.enable_messages` | Enables or disables automatic restart announcements. |
| `restart.times` | Daily restart times in 24-hour `HH:mm` format. |
| `restart.warn_minutes` | Minute marks before restart when warnings are broadcast. |
| `restart.command` | Command to execute at restart time if `execute_at_zero` is enabled. |
| `restart.execute_at_zero` | Runs `restart.command` when the countdown reaches zero. |
| `rules.rules` | Lines shown by `/rules`. |
| `rules.discord_url` | Discord link shown by `/rules`; leave blank to hide it. |
| `rules.website_url` | Website link shown by `/rules`; leave blank to hide it. |
| `maintenance.enabled` | Enables maintenance mode for non-operator players. |
| `maintenance.message` | Disconnect message shown while maintenance mode is enabled. |
| `aliases.commands` | Top-level command aliases in `alias=target command` format. |
| `ftb_chunks.unload_inactive_enabled` | Enables automatic inactive-team force-load cleanup when the FTB mods are present. |
| `ftb_chunks.inactive_days` | Number of real-world inactive days before a team's chunks stop being force-loaded. |
| `ftb_chunks.check_interval_minutes` | Minutes between automatic cleanup checks. |

All restart times use the server's local timezone.

---

## FTB Chunks Integration

The integration requires FTB Chunks, FTB Teams, and FTB Library. They remain optional dependencies; without them, Server Helper logs that the integration is inactive and all other features continue working.

Automatic cleanup uses the last-login timestamp maintained by FTB Chunks for the chunk-owning team. Once that timestamp is older than `ftb_chunks.inactive_days`, the team's force-loaded chunks are unloaded and saved as normal FTB claim data. The chunks remain claimed.

To remove every FTB Chunks force-load immediately:

```text
/serverhelper ftbchunks unloadall
```

This command is independent of `unload_inactive_enabled`, so operators can use it even when automatic cleanup is disabled.

---

## Maintenance Mode

Maintenance mode is useful when updating mods, changing configs, repairing world data, or testing before reopening the server.

Enable it with:

```text
/serverhelper maintenance on
```

Disable it with:

```text
/serverhelper maintenance off
```

While enabled, non-operator players are disconnected after login with the configured maintenance message. Operators can still join so they can test and administer the server.

---

## Performance Snapshots

`/serverhelper status` provides a lightweight live snapshot:

- Server TPS and average MSPT.
- Online player count.
- Maintenance mode state.
- Per-dimension MSPT.
- Per-dimension entity count.
- Per-dimension player count.
- Per-dimension loaded chunk count.

This is intended as a quick admin overview. For deep profiling, use a dedicated profiler such as Spark.

---

## Command Aliases

Aliases are configured in `aliases.commands` using `alias=target command` entries:

```toml
[aliases]
	commands = [
		"discord=rules",
		"website=rules",
		"restart=serverhelper restartstatus"
	]
```

Aliases execute with the caller's normal command permissions. They do not bypass operator checks.

New aliases can be added with `/serverhelper reload`. Removing an alias from the command tree requires a server restart, but removed aliases stop executing after reload.

Reserved command roots such as `serverhelper`, `banitems`, `enchant`, `repair`, and `rules` are ignored as aliases.

`afk`, `seen`, `staff`, and `vanish` are also reserved because they are built-in commands.

---

## AFK Status

Players can run:

```text
/afk
```

When a player becomes AFK, the server broadcasts a chat message and shows `[AFK]` next to the player's name in the tab menu. The tab-list marker is applied through Forge's tab-list name formatting event and preserves the existing display name component, which helps it cooperate with name/chat formatting mods such as Better Forge Chat Reborn.

Players automatically become AFK after 10 minutes without activity. Moving, chatting, interacting with an entity, or running `/afk` again removes AFK status and broadcasts that the player has returned.

---

## Staff Tools

`/staff` lists online players who have the `server_helper_mod.staff` permission. Vanilla operators are treated as staff by default, and LuckPerms can grant or deny the node directly.

Example LuckPerms command:

```text
/lp user <player> permission set server_helper_mod.staff true
```

Players without staff permission do not see vanished staff in `/staff`, while staff can see vanished staff marked with `[VANISHED]`.

`/seen <player>` requires `server_helper_mod.staff` or vanilla operator permissions and shows whether a player is online. For online players, it reports their current dimension, coordinates, AFK status, and vanish status. For offline players, it reports the last stored logout time, last login time, and last-known location from `server_helper_mod_seen_players.json`.

`/vanish` requires `server_helper_mod.staff` or vanilla operator permissions. It hides the vanished staff member from non-staff tab lists and applies no-particle invisibility. Other staff are notified when a staff member toggles vanish.

`/enchant <enchantment>` requires `server_helper_mod.staff` or vanilla operator permissions. It applies the requested enchantment at that enchantment's maximum level to the staff member's main-hand item. The enchantment must be valid for the held item.

`/repair` requires `server_helper_mod.staff` or vanilla operator permissions. It fully repairs the staff member's main-hand item.

---

## Banned Items File

The banned items JSON file maps item registry IDs to either `soft` or `hard`:

```json
{
  "minecraft:bedrock": "hard",
  "minecraft:tnt": "soft"
}
```

You can edit this file directly and run `/serverhelper reload` or `/banitems reload`, or manage it in-game with `/banitems`.

---

## Reload Behavior

`/serverhelper reload` refreshes all runtime data currently managed by the mod:

- Reloads the Forge common config from disk.
- Rebakes restart settings, rules, Discord link, and website link.
- Rebakes maintenance mode settings and command aliases.
- Resets the restart scheduler using the new values.
- Reloads `server_helper_mod_banned_items.json`.
- Reloads `server_helper_mod_seen_players.json`.
- Sweeps online player inventories, ender chests, open containers, and tracked loaded chunk containers.

---

## Installation

1. Install **Minecraft Forge 1.20.1 (47.x)** on your server.
2. Place the mod JAR into the server's `mods/` directory.
3. Start the server once to generate config files.
4. Edit the config files as needed.
5. Run `/serverhelper reload` or restart the server.

---

## Building From Source

Use the included Gradle wrapper. ForgeGradle does not currently support Gradle 9.x for this project, so do not use a globally installed Gradle 9 build command.

On Windows PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

On macOS/Linux:

```bash
./gradlew build
```

Build output is written under `build/libs/`.

---

## Compatibility

- Minecraft **1.20.1**
- Forge **47.x**
- Dedicated servers
- Java **17**
- Optional support for FTB Chunks/Teams/Library **2001.x** on Minecraft 1.20.1

This mod does not add gameplay content and is intended for server administration use.

---

## License

MIT License
