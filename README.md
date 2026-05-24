# Server Helper Mod

**Server Helper Mod** is a lightweight, server-side Forge mod for **Minecraft 1.20.1** that helps with common server administration tasks: scheduled restart warnings, configurable server rules, and banned-item enforcement.

The mod is intended for dedicated servers and does not require players to install anything on their clients.

---

## Features

- **Scheduled restarts**
  - Define one or more daily restart times in server local time.
  - Broadcast configurable warning messages before the restart.
  - Optionally run a server command when the countdown reaches zero.

- **Rules command**
  - Players can run `/rules` to view configured server rules.
  - Optional Discord and website links are shown as clickable chat links.

- **Banned item management**
  - Add, remove, reload, and list banned items in-game.
  - Soft bans block placement/deployment behavior and remove soft-banned items from automation containers such as dispensers and droppers.
  - Hard bans block use and remove matching item stacks from player inventories, ender chests, open containers, and loaded containers.

- **Admin utilities**
  - Reload all mod data without restarting the server.
  - Check the next scheduled restart.
  - Send test restart warnings.
  - Check the server's local time.

---

## Commands

### Player Commands

| Command | Description |
| --- | --- |
| `/rules` | Shows the configured server rules and helpful links. |
| `/banitems list` | Lists currently banned items. |

### Operator Commands

| Command | Description |
| --- | --- |
| `/serverhelper reload` | Reloads config, rules, restart schedule, banned items, and sweeps loaded inventories/containers. |
| `/serverhelper testwarn <minutes>` | Broadcasts a test restart warning. |
| `/serverhelper restartstatus` | Shows the next scheduled restart and remaining seconds. |
| `/serverhelper getlocaltime` | Shows the server's local date/time. |
| `/banitems reload` | Reloads the banned item file and sweeps loaded inventories/containers. |
| `/banitems hardban hand` | Hard-bans the item currently held in your main hand. |
| `/banitems hardban <item>` | Hard-bans an item by registry ID, such as `minecraft:bedrock`. |
| `/banitems softban hand` | Soft-bans the item currently held in your main hand. |
| `/banitems softban <item>` | Soft-bans an item by registry ID. |
| `/banitems unban hand` | Removes the ban for the item currently held in your main hand. |
| `/banitems unban <item>` | Removes a ban by registry ID. |

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

All restart times use the server's local timezone.

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
- Resets the restart scheduler using the new values.
- Reloads `server_helper_mod_banned_items.json`.
- Sweeps online player inventories, ender chests, open containers, and tracked loaded chunk containers.

---

## Installation

1. Install **Minecraft Forge 1.20.1 (47.x)** on your server.
2. Place the mod JAR into the server's `mods/` directory.
3. Start the server once to generate config files.
4. Edit the config files as needed.
5. Run `/serverhelper reload` or restart the server.

---

## Compatibility

- Minecraft **1.20.1**
- Forge **47.x**
- Dedicated servers

This mod does not add gameplay content and is intended for server administration use.

---

## License

MIT License
