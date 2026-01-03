# 💎 CrystalCore - Hoja de Ruta de Desarrollo

## 📝 Funcionalidades Planeadas

### 🟢 Sistema de Estado del Staff (Reemplazo para Plan)

### 🟢 Sistema de Estado del Staff (Reemplazo para Plan)

- [x] **Sincronización de Estado en Tiempo Real**: Implementar un sistema de actualización de estado directo para evitar la latencia del plugin `Plan`.
  - **Concepto**: Usar `PlayerJoinEvent` y `PlayerQuitEvent` para actualizar instantáneamente una tabla `staff_status` en MySQL (o clave Redis).
  - **Objetivo**: Asegurar que el Panel Web refleje el estado online distintivo (Minecraft vs Discord) con cero latencia.
  - **Detalles**:
    - Al Entrar: Actualizar DB `status = 'online'`, `last_seen = NOW()`.
    - Al Salir: Actualizar DB `status = 'offline'`.

### 🟡 Puente de Base de Datos Banco (Sincronización Gacha)

- [x] **Integración SQLite**: Añadir dependencia `org.xerial:sqlite-jdbc` al `pom.xml`.
- [x] **Lector de Banco**: Crear un servicio para leer `plugins/banco/accounts.db`.
  - **Objetivo**: Sincronizar balances de jugadores y propiedad de Killucoins con el Gacha Web.
  - **Lógica**: Usar JDBC para consultar el archivo SQLite local directamente.
- [x] **Escáner de Inventario**: Implementar utilidad para detectar ítems por `CustomModelData` (ej. Ultra_gema).
  - **Objetivo**: Desbloquear niveles del Gacha basado en posesión física de ítems.

### 🌐 WebSocket (Tiempo Real)

- [x] **Servidor WebSocket**: Implementar servidor en puerto 8887.
  - **Seguridad**: Token de autenticación en Handshake.
  - **Funciones**: `broadcast` (Alertas) y `listen` (Comandos remotos).

### 💰 Economía y Comandos

- [x] **Comando `/money`**: Implementar visualización de balance personalizada.
  - **Salida**: "Tienes: [Balance] Killucoins!"
  - **Tabla de Tasa de Cambio**:
    - Bronce: 1
    - Plata: 100
    - Oro: 10,000 (10K)
    - Esmeralda: 1,000,000 (1M)
    - Diamante: 100,000,000 (100M)
    - Iridio: 1,000,000,000 (1B)

## 🐛 Errores y Correcciones

- [ ] (Vacío)
