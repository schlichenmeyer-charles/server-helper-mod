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
> This mod does **not** restart the server process itself.  
> You must use a wrapper, script, or service manager (systemd, AMP, Docker, etc.) to restart the server after it stops.

---

## Configuration

After the server runs once, a config file will be generated at:

config/server_helper_mod-server.toml


### Example Configuration

```toml
enable_messages = true

restart_times = ["04:00", "16:00"]

warn_minutes = [30, 10, 5, 1]
```

### Configuration Options

| Option | Description |
|------|------------|
| `enable_messages` | Enable or disable restart announcements |
| `restart_times` | Daily restart times in 24-hour `HH:mm` format |
| `warn_minutes` | Minutes before restart to send warning messages |

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

MIT License (or update this section to match your project)

