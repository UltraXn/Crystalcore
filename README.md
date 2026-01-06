# 💎 CrystalCore

El **plugin central de PaperMC** para el servidor _CrystalTides SMP_. Maneja las mecánicas centrales del juego, economía, ítems personalizados y sincroniza datos con la plataforma web.

## ✨ Características

- **Sincronización de Base de Datos**: Soporta MySQL/SQLite para datos persistentes de jugadores.
- **Integración Web**: Sincroniza rangos y estadísticas con la web vía WebSocket/Rest.
- **Economía**: Manejo de moneda personalizada.
- **PlaceholderAPI**: Exporta placeholders personalizados para uso en otros plugins (Tab, Scoreboard).

## 🛠️ Compilación e Instalación

Este proyecto usa **Maven** para la gestión de dependencias.

### Prerrequisitos

- JDK 21
- Maven

### Compilación

```bash
mvn clean package
```

El jar resultante estará en `target/CrystalCore-1.4-SNAPSHOT.jar`.

## ⚙️ Configuración

El archivo `config.yml` (generado en la primera ejecución) maneja las conexiones a base de datos:

```yaml
database:
  type: 'mysql' # o sqlite
  host: 'localhost'
  port: 3306
  database: 'crystaltides'
  username: 'usuario'
  password: 'password'
```

## 📦 Dependencias

- **Paper API** (1.21.1)
- **HikariCP** (Pooling de Base de Datos)
- **PlaceholderAPI**
- **Java-WebSocket**
