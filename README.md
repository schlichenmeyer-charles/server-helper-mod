# Server Helper Mod

**Server Helper Mod** is a lightweight, server-side Forge mod for **Minecraft 1.20.1** that automatically notifies players in chat when the server is approaching a scheduled restart.

The goal of this mod is to provide clear, configurable restart warnings without requiring plugins, command blocks, or client-side mods.

---

## Features

- ⏰ **Scheduled restarts**
  - Define one or more daily restart times
- 💬 **Automatic chat warnings**
  - Broadcast countdown messages at configurable intervals
- ⚙️ **Server-side only**
  - No client installation required
- 🔁 **Clean restarts**
  - Safely stops the server so your service manager can restart it

---

## How It Works

- The mod reads a restart schedule from a TOML config file
- On server ticks, it checks how long remains until the next restart
- At configured times (e.g. 30, 10, 5, 1 minute remaining), it sends chat warnings
- When the countdown reaches zero, the mod issues a clean `/stop`

> ⚠️ **Important:**  
> This mod has the option, defined by the Config file, to issue a command or not
> If your hosting provider does not support restarts through the console use the automated tools from the hosting provided and disable the command execution.

---

## Configuration

After the server runs once, a config file will be generated at:

config/server_helper_mod-server.toml


### Example Configuration

```toml

[general]
	#Turn on and off automatic restart messages.
	enable_messages = true

[restart]
	#Daily restart times (24h HH:mm) in server local time. e.g. ["04:00","16:00"]
	times = ["04:00"]
	#Send warnings when restart is N minutes away.
	warn_minutes = [30, 10, 5, 1]
	#Server command to execute at restart time (no leading '/'). Example: 'stop' or 'restart'
	command = "stop"
	#Whether the server should be stopped when the countdown reaches zero.
	stop_at_zero = false


```

### Configuration Options

| Option | Description |
|------|------------|
| `enable_messages` | Enable or disable restart announcements |
| `times` | Daily restart times in 24-hour `HH:mm` format |
| `warn_minutes` | Minutes before restart to send warning messages |
| `command` | The command that the server executes when the countdown reaches zero |
| `stop_at_zero` | Determines if the above command runs when the countdown reaches zero |

> All times use the server’s local timezone.

---

## Installation

1. Install **Minecraft Forge 1.20.1 (47.x)**
2. Place the mod JAR into your server’s `mods/` directory
3. Start the server once to generate the config file
4. Edit the config and restart the server

---

## Compatibility

- Minecraft **1.20.1**
- Forge **47.x**
- Dedicated servers only

This mod does not add gameplay content and is intended for server administration use.

---

## License

MIT License

