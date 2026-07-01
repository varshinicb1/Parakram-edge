# Parakram - Pocket Edge Server & Developer Hub
## Dynamic AI Agent Architecture & Protocol Documentation

This document serves as a complete blueprint, architectural reference, and instruction set for any AI agent or systems integrator continuing the development of Parakram (Pocket Edge Server). 

---

## 1. Core Architectural Pillars

Parakram is a localized mobile edge server running a micro-architecture designed to bridge remote desktop automation, desktop AI agents, and local mobile hardware/telemetry.

### A. Core Engine Components
1. **Ktor CIO Mobile Web Server (`MobileServerManager.kt`)**: 
   - A lightweight asynchronous Kotlin web server running on CIO (Coroutine-based I/O).
   - Manages state, handles authenticated API routes, dynamic databases, real-time files, and sensor feeds.
2. **Foreground Keeping Service (`ServerForegroundService.kt`)**:
   - Keeps the server alive in the background under strict Android 14+ (API 34) and Android 15+ (API 35) power constraints.
3. **Telemetry Sentinel (`BatteryMonitoringService.kt`)**:
   - Periodically samples battery parameters, voltage, temperatures, and charging states, broadcasting via shared Kotlin `StateFlow`s.

---

## 2. Our Invented Protocol: UTAP (Universal agentic Table Access Protocol)

To eliminate the need for error-prone raw SQL queries transmitted from external AI agents, Parakram invents and implements the **UTAP** protocol. It exposes standard REST endpoints that allow AI agents to dynamically declare schemas and execute secure CRUD operations on a local SQLite database on the phone.

### UTAP API Endpoints

All UTAP endpoints are authenticated via the `X-Agent-Key` header.

#### 1. Define Table Schema
* **Route**: `POST /api/db/utap/schema`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "columns": [
      "id INTEGER PRIMARY KEY AUTOINCREMENT",
      "key TEXT UNIQUE",
      "value TEXT",
      "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
    ]
  }
  ```
* **Behavior**: Dynamically executes `CREATE TABLE IF NOT EXISTS` sanitizing definitions.

#### 2. Insert Record (Create)
* **Route**: `POST /api/db/utap/create`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "data": {
      "key": "user_preference_theme",
      "value": "obsidian_dark"
    }
  }
  ```
* **Behavior**: Uses parameterized SQLite statements under the hood to safely insert data and returns `rowId`.

#### 3. Query Records (Read)
* **Route**: `POST /api/db/utap/read`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "where": "key = 'user_preference_theme'",
    "orderBy": "timestamp DESC",
    "limit": 1
  }
  ```
* **Behavior**: Queries the database dynamically and returns key-value mapping rows in JSON.

#### 4. Update Records (Update)
* **Route**: `POST /api/db/utap/update`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "data": {
      "value": "nebula_cyan"
    },
    "where": "key = 'user_preference_theme'"
  }
  ```
* **Behavior**: Safely executes updates, parameterized to prevent any SQL injection.

#### 5. Delete Records (Delete)
* **Route**: `POST /api/db/utap/delete`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "where": "id = 1"
  }
  ```

#### 6. List Dynamic Tables
* **Route**: `GET /api/db/utap/tables`
* **Response**: Returns a list of all custom developer tables and their associated SQL schemas.

---

## 3. Play Store Compliance & Android 14+ (API 34/35) Security Mandates

Android 14+ introduces tight regulations regarding foreground services to protect battery and prevent malicious apps.

### A. Declaring Proper Service Types
In `/app/src/main/AndroidManifest.xml`, both services specify strict, Play-Store-compliant foreground types:
- `ServerForegroundService` uses `dataSync` to enable real-time local file exchange and terminal command streams.
- `BatteryMonitoringService` uses `specialUse` to track ongoing sensor logs.

### B. Special Use Subtype Property (CRITICAL)
For services using `specialUse`, Google Play and the Android OS *require* a `<property>` tag to avoid instant runtime verification failure:
```xml
<service
    android:name=".data.BatteryMonitoringService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Telemetry monitoring of battery state and developer sensors to expose to desktop agents over dynamic endpoint." />
</service>
```

### C. Run-time Code Invocation
Always call `startForeground()` passing the correct service type bitmask on API Q+:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
```

---

## 4. Play Store Scaling & Monetization Guide

To transition Parakram into a high-revenue, multi-million download app, execute the following steps:

### A. Ad Revenue Integration (Google Mobile Ads SDK)
1. Add the play-services-ads library to `libs.versions.toml`:
   ```toml
   play-services-ads = { group = "com.google.android.gms", name = "play-services-ads-lite", version = "23.0.0" }
   ```
2. Initialize AdMob in `MainActivity.kt` on startup:
   ```kotlin
   MobileAds.initialize(this) {}
   ```
3. Use specialized Jetpack Compose Banner or Interstitial wrappers to display developer and pro ads to the user.

### B. Firebase Analytics & Play Console Stability Tracking
- **User Engagement**: We have integrated `firebase-analytics`. Custom events are fired on login, sever state toggles, pairing events, and issue submissions.
- **Support Flow**: Users can submit diagnostic telemetry directly through the "Support & Report Center" inside the Settings/Secure tab. Submitted tickets trigger a custom Firebase analytical event `stability_report_logged` with complete device profiles.

---

## 5. Modern Visual Onboarding, App Shortcuts & Analytics Event Protocol

Parakram is equipped with modern user experience flows and telemetry analytics to maximize conversion, retention, and integration:

### A. Immersive Welcome Onboarding Overlay
- **Behavior**: On first boot or whenever requested, an immersive onboarding overlay displays to introduce users to Pocket Edge Server capabilities, security settings, and permissions.
- **State**: Controlled dynamically in `DeviceAPIScreen` via a persistent/saveable state (`showWelcomeScreen`).
- **Telemetry**: Dismissing the onboarding triggers a specialized Firebase Analytics metric `welcome_dismissed` to track user onboarding completion rates.

### B. Dynamic Launcher App Shortcuts
To enable users to instantly jump to specific server dashboards directly from their home screen, Parakram registers static Launcher Shortcuts (`/app/src/main/res/xml/shortcuts.xml`).
- **Shortcuts Configured**:
  1. **Dashboard Shortcut**: Instantly opens the main developer status tab.
  2. **Automation Shortcut**: Bypasses the welcome screen and opens the local automation flow.
  3. **Console Shortcut**: Directly enters the real-time diagnostics terminal.
- **Protocol**: 
  - Android home screen launcher triggers `MainActivity` with intent action `"com.example.ACTION_SHORTCUT"` and a string extra `tab`.
  - `MainActivity` intercepts the action and calls `viewModel.requestTab(tab)`.
  - `DeviceAPIScreen` collects `requestedTab` state flow, updates `currentTab`, dismisses the welcome screen overlay, and clears the pending request.
  - Switches trigger an analytics event `tab_selected` tracking the specific sub-tab user engagement.

### C. Physical QR & CameraX ML Kit Automation Engine (Fakeness-Free)
- **Core Technology**: Integrated Android `CameraX` framework (`Preview`, `ImageAnalysis`) with Google `ML Kit Barcode Scanning` library.
- **Real-Time Analysis**: Captured image frames are passed to a highly efficient `QRCodeAnalyzer` which decodes QR codes on-device, triggering immediate tactile feedback via the device's physical `Vibrator` motor (using API-compliant haptic amplitude effects).
- **Physical NFC Tag Discovery**: Captured `ACTION_NDEF_DISCOVERED` intents are intercepted in `MainActivity` and piped into the trigger evaluator as the real `"NFC Tag Detected"` event, bridging physical-to-digital edge server workflows.
- **Declarative Mappings**: Exposes a dedicated management UI allowing users to dynamically register, toggle, or delete pattern-to-action bindings. For example, scanning a barcode containing `SECURE_DOOR_ACCESS` instantly runs "Trigger Phone Haptics", while `CAMERA_PHOTO_CAPTURE` takes a front sensor camera snap.

---

## 6. AgentOS Bridge — Relay Infrastructure, SDKs & Plugin Marketplace

New in v2.0.0: Parakram extends beyond LAN with a Cloudflare-backed relay infrastructure, official SDKs for AI agents, and a plugin marketplace.

### A. Cloudflare Workers Relay (Cross-Network Connectivity)

**Architecture**: Phone (mobile data) ↔ Cloudflare Durable Object ↔ Agent (desktop LAN)

- **relay/src/index.ts**: Worker entry — accepts WebSocket upgrades at `/connect?deviceId=xxx&role=phone|agent&token=yyy`, authenticates via `RELAY_TOKEN`, routes to Durable Object by deviceId.
- **relay/src/relay-room.ts**: Durable Object (`RelayRoom`) — maintains two WebSocket slots (phone + agent), relays messages bidirectionally, handles disconnects via Hibernation API for cost efficiency.
- **relay/wrangler.jsonc**: Defines `RELAY_ROOM` binding, migration `v1`, env var `RELAY_TOKEN`.

**Protocol**:
1. Phone calls `MobileServerManager.connectToRelay(relayUrl, relayToken)` → opens WebSocket as `role=phone`.
2. Agent SDK calls `connectRelay(deviceId, apiKey)` → opens WebSocket as `role=agent`.
3. Agent sends JSON: `{ "id": "uuid", "method": "GET", "url": "/api/status", "body": null }`
4. Phone receives, executes local HTTP call to `127.0.0.1:8080`, returns response via relay.
5. Response: `{ "id": "uuid", "status": 200, "body": "{\"status\":\"online\"}" }`

**Tier Routing**:
- **Free**: LAN-only (mDNS discovery, direct connection)
- **Pro ($5/mo via Razorpay)**: Internet relay via Cloudflare Workers
- **Enterprise ($50/seat/mo)**: Fleet management, SSO, audit logs, priority relay capacity

### B. Python Agent SDK (`sdk/python/parakram/`)

```bash
pip install parakram-bridge
```

```python
from parakram import Parakram

# Local discovery (LAN)
devices = Parakram().discover()
agent = Parakram().connect(devices[0])

# Cloud relay (internet)
agent = Parakram(relay_url="wss://relay.parakram.dev", relay_token="xxx").connect_relay("device-id", "sk_agent_...")

# Capabilities
caps = agent.capabilities
sensors = agent.sensors

# Camera stream (HMAC-signed URL)
stream_url = agent.camera_auth()

# UTAP database
agent.utap_create("logs", {"key": "event", "value": "login"})
rows = agent.utap_read("logs", where="key='event'")
```

**Files**: `client.py` (HTTP + mDNS discovery), `models.py` (dataclasses), `__init__.py` (exports).

### C. TypeScript Agent SDK (`sdk/typescript/`)

```bash
npm install parakram-bridge
```

```typescript
import { Parakram } from "parakram-bridge";

const agent = new Parakram({ relayUrl: "wss://relay.parakram.dev", relayToken: "xxx" })
  .connectRelay("device-id", "sk_agent_...");

const caps = await agent.capabilities();
const streamUrl = await agent.cameraAuth();
const rows = await agent.utapRead("logs", "key='event'");
```

### D. Plugin Registry API (`registry/`)

Cloudflare Worker + D1 database for plugin marketplace.

**Endpoints**:
- `GET /api/plugins?category=utility` — list plugins
- `POST /api/plugins` — create plugin (requires `X-Registry-Key`)
- `GET /api/plugins/:name` — get plugin details
- `POST /api/plugins/:name/versions` — publish version
- `POST /api/install` — record installation: `{ plugin_name, device_id, version }`
- `GET /api/installed/:deviceId` — list installed plugins

**Schema** (`registry/schema.sql`):
- `plugins(id, name, display_name, description, author, category, icon_url, tier, homepage)`
- `versions(plugin_id, version, code_url, min_sdk_version, changelog, checksum)`
- `installations(plugin_id, device_id, version, enabled)`

**Tier Enforcement**: `tier` column (`free|pro|enterprise`) controls availability. Enterprise tier plugins require valid subscription (checked via Razorpay webhook).

### E. MobileServerManager Relay Integration

```kotlin
// In MobileServerManager
fun connectToRelay(relayUrl: String, relayToken: String) { ... }
fun disconnectFromRelay() { ... }
val isRelayConnected: StateFlow<Boolean>
val relayDeviceId: StateFlow<String>

// Usage in DeviceAPIViewModel:
viewModel.connectToRelay("wss://relay.parakram.dev", "RELAY_TOKEN")
```

Uses OkHttp WebSocket (`okhttp3.WebSocketListener`) with ping interval 30s. On message receipt, executes local HTTP call to Ktor server and returns response via relay.

---

## 7. Razorpay Billing Integration

Replaces Stripe for Indian market compliance and UPI support.

### A. Billing Worker (`billing/`)

**Endpoints**:
- `POST /billing/create-subscription` — creates Razorpay subscription, returns `subscription_id`
- `POST /billing/webhook` — Razorpay webhook handler (verifies signature)
- `GET /billing/tiers` — returns tier config (Free/Pro/Enterprise pricing)

**Razorpay Plans**:
- Free: ₹0 (LAN-only)
- Pro: ₹499/mo (Internet relay, 10 devices)
- Enterprise: ₹4999/seat/mo (Fleet, SSO, audit, priority)

**Webhook Events**:
- `subscription.charged` — activate Pro/Enterprise tier
- `subscription.cancelled` — downgrade to Free
- `payment.failed` — retry logic, notify user

### B. Tier Enforcement in Registry

Registry checks device tier via Razorpay customer ID stored in D1. Enterprise plugins only installable if `device.tier = 'enterprise'`.

---

## 8. Enterprise Features

### A. SSO (Google OAuth)
- Registry `/auth/sso/google` initiates OAuth flow
- JWT issued with `org_id`, `role` claims
- Fleet admin can invite members via email

### B. Audit Logging
- `POST /api/audit` — records `{ actor, action, resource, timestamp, metadata }`
- Immutable append-only table in D1
- Export via `GET /api/audit?from=...&to=...`

### C. Fleet Management
- `POST /api/fleet/devices` — register device with fleet token
- `GET /api/fleet/devices` — list all devices, status, tier
- `POST /api/fleet/policy` — push config (allowed plugins, network rules)

---

## 9. Development Guidelines for Future Agents

- **M3 Touch Targets**: Every button must be exactly `48.dp` in height (or use `Modifier.height(48.dp)`) to provide top-tier touch precision on hardware screens.
- **State Management**: Keep ViewModels free of raw database connections; always route through Ktor or local repositories.
- **Compile and Build**: Verify all changes by running `compile_applet` to confirm compilation is green.