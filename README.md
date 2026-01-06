# 💎 CrystalCore

The central **PaperMC plugin** for the _CrystalTides SMP_ server. It handles core game mechanics, economy, custom items, and synchronizes data with the web platform.

## ✨ Features

- **Database Sync**: Supports MySQL/SQLite for persistent player data.
- **Web Integration**: syncs ranks and stats with the website via WebSocket/Rest.
- **Economy**: Custom currency handling.
- **PlaceholderAPI**: Exports custom placeholders for use in other plugins (Tab, Scoreboard).

## 🛠️ Build & Install

This project uses **Maven** for dependency management.

### Prerequisites

- JDK 21
- Maven

### Building

```bash
mvn clean package
```

The output jar will be in `target/CrystalCore-1.4-SNAPSHOT.jar`.

## ⚙️ Configuration

The `config.yml` (generated on first run) handles database connections:

```yaml
database:
  type: 'mysql' # or sqlite
  host: 'localhost'
  port: 3306
  database: 'crystaltides'
  username: 'user'
  password: 'password'
```

## 📦 Dependencies

- **Paper API** (1.21.1)
- **HikariCP** (Database Pooling)
- **PlaceholderAPI**
- **Java-WebSocket**
