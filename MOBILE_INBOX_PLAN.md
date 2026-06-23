# Mobile Inbox — Implementation Plan

## Modules

| Module | Purpose |
|--------|---------|
| `sdk/mobile-inbox` | Data layer: Room DB, remote API, repository, public API object |
| `sdk/mobile-inbox-ui` | Stretch: Compose UI components and theming |

Both are added to `settings.gradle.kts` includes.

---

## Module: `sdk/mobile-inbox`

### Gradle (`sdk/mobile-inbox/build.gradle.kts`)

Dependencies:
- `implementation(project(":sdk:core"))` — Registry, logging, config, lifecycle
- `implementation(project(":sdk:analytics"))` — pulls in `KlaviyoApiClient` networking infra (TBD: may use raw `HttpURLConnection` if endpoint auth differs from analytics API key flow)
- `implementation(project(":sdk:push-fcm"))` — `KlaviyoRemoteMessage` extensions, `KlaviyoPushService`
- Room: `implementation(AndroidX.room.runtime)`, `implementation(AndroidX.room.ktx)`, `ksp(AndroidX.room.compiler)`
- `implementation(KotlinX.coroutines.android)` (likely transitive already)

---

### Package: `com.klaviyo.mobileInbox`

#### Data Layer

**`InboxStatus.kt`**
```kotlin
enum class InboxStatus { UNREAD, READ, HIDDEN }
```

**`InboxMessageEntity.kt`** — Room entity
```
@PrimaryKey id: String          // RemoteMessage.messageId (push) or server-assigned id (remote fetch)
timestamp: Long                  // epoch millis — sentAt from server, or System.currentTimeMillis() for push
title: String
body: String
status: InboxStatus              // default UNREAD; never downgraded by remote sync
source: InboxSource              // PUSH | REMOTE — for debugging/filtering
```

`source` enum: `PUSH` (received via FCM), `REMOTE` (fetched from endpoint only).

**`InboxMessageDao.kt`**
```kotlin
@Upsert fun upsertMessages(messages: List<InboxMessageEntity>)
@Query("SELECT * FROM inbox_messages WHERE status != 'HIDDEN' ORDER BY timestamp DESC")
fun observeMessages(): Flow<List<InboxMessageEntity>>
@Query("SELECT * FROM inbox_messages WHERE status != 'HIDDEN' ORDER BY timestamp DESC")
suspend fun getMessages(): List<InboxMessageEntity>
@Query("UPDATE inbox_messages SET status = :status WHERE id = :id")
suspend fun updateStatus(id: String, status: InboxStatus)
@Query("SELECT COUNT(*) FROM inbox_messages WHERE status = 'UNREAD'")
fun observeUnreadCount(): Flow<Int>
```

Upsert merge rule (enforced in repository, not DAO): remote sync never overwrites `status` if the existing row is `READ` or `HIDDEN`.

**`InboxDatabase.kt`** — `@Database(entities=[InboxMessageEntity::class], version=1)`, companion object singleton taking `Context`.

---

#### Domain Model

**`InboxMessage.kt`** — clean data class mirroring entity (no Room annotations), used in public API and UI.

---

#### Push Integration

**`KlaviyoInboxPushService.kt`** — convenience base class:
```kotlin
open class KlaviyoInboxPushService : KlaviyoPushService() {
    override fun onKlaviyoNotificationMessageReceived(message: RemoteMessage) {
        super.onKlaviyoNotificationMessageReceived(message)
        KlaviyoMobileInbox.handlePushMessage(message)
    }
}
```

Developers who already have their own push service subclass just call `KlaviyoMobileInbox.handlePushMessage(message)` themselves.

ID extraction: `RemoteMessage.messageId` (Firebase-assigned, unique per message).
Fallback if `messageId` is null: generate a UUID and log a warning.

---

#### Remote API

**`InboxApiService.kt`** — interface:
```kotlin
interface InboxApiService {
    suspend fun fetchMessages(profileParams: InboxProfileParams): List<InboxMessage>
}
```

**`InboxProfileParams.kt`** — data class holding whichever identifiers are available:
```kotlin
data class InboxProfileParams(
    val anonymousId: String?,
    val email: String?,
    val phoneNumber: String?
)
```
Built at call time from `Registry` state (profile/identity store). At least one field must be non-null; if all are null, skip the remote fetch and log a warning.

**`InboxApiServiceImpl.kt`** — HTTP GET to the inbox endpoint (URL configured via `KlaviyoMobileInbox.initialize`). Uses `HttpURLConnection` or the existing OkHttp instance from analytics (decision deferred until URL + auth scheme is confirmed). Query params appended from `InboxProfileParams`.

Response JSON model: `[{ "id": "...", "timestamp": 1234567890, "title": "...", "body": "..." }]`

---

#### Repository

**`InboxRepository.kt`** interface:
```kotlin
interface InboxRepository {
    fun observeMessages(): Flow<List<InboxMessage>>   // stale-while-revalidate
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(id: String)
    suspend fun markHidden(id: String)
    suspend fun sync()                                 // explicit trigger (also called on resume)
}
```

**`InboxRepositoryImpl.kt`** — stale-while-revalidate behavior:
- `observeMessages()` returns a `Flow` that emits the current Room state immediately, then triggers `sync()` on first collection, re-emitting as Room updates propagate.
- `sync()`: fetch from remote → upsert into Room (preserving `READ`/`HIDDEN` statuses) → Room Flow emits updated list automatically.

---

#### Public API

**`KlaviyoMobileInbox.kt`**
```kotlin
object KlaviyoMobileInbox {
    fun initialize(context: Context, inboxEndpointUrl: String)
    fun handlePushMessage(message: RemoteMessage)     // call from push service
    fun observeMessages(): Flow<List<InboxMessage>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(id: String)
    suspend fun markHidden(id: String)
    suspend fun sync()                                // manual trigger
}
```

`initialize` registers a `Registry.lifecycleMonitor.onActivityEvent` observer that calls `sync()` on `ActivityEvent.Resumed`.

---

## Module: `sdk/mobile-inbox-ui` (stretch)

Dependencies:
- `implementation(project(":sdk:mobile-inbox"))`
- Compose BOM, `androidx.compose.material3`

### Components

**`InboxTheme.kt`** — data class:
```kotlin
data class InboxTheme(
    val unreadBadgeColor: Color = Color(0xFFE53935),
    val backgroundColor: Color = MaterialTheme.colorScheme.background,
    val titleColor: Color = MaterialTheme.colorScheme.onBackground,
    val bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    val unreadIndicatorColor: Color = Color(0xFF1E88E5)
)
```

**`InboxListScreen`** — `@Composable` LazyColumn; each row shows title, body preview, timestamp, unread dot.

**`InboxDetailScreen`** — `@Composable` full message view; calls `markRead` on first display.

**`UnreadBadge`** — small `@Composable` circle with count; hides at zero.

---

## Work Breakdown

### Phase 1 — Data layer
- [ ] Create `sdk/mobile-inbox/` directory + `build.gradle.kts`
- [ ] Add to `settings.gradle.kts`
- [ ] `InboxStatus`, `InboxSource` enums
- [ ] `InboxMessageEntity`, `InboxMessage` domain model
- [ ] `InboxMessageDao`
- [ ] `InboxDatabase`

### Phase 2 — Push integration
- [ ] `KlaviyoMobileInbox.initialize` + `handlePushMessage`
- [ ] `KlaviyoInboxPushService` base class
- [ ] Unit tests: push received → entity stored with UNREAD status

### Phase 3 — Remote API + Repository
- [ ] `InboxProfileParams`, `InboxApiService` interface
- [ ] `InboxApiServiceImpl` (placeholder URL, fill in when endpoint is ready)
- [ ] `InboxRepositoryImpl` (stale-while-revalidate, upsert merge logic)
- [ ] Unit tests: sync merges without downgrading READ/HIDDEN status

### Phase 4 — Public API + lifecycle sync
- [ ] `KlaviyoMobileInbox` — remaining methods + `Resumed` lifecycle hook
- [ ] Integration test in sample app

### Phase 5 (stretch) — UI module
- [ ] `sdk/mobile-inbox-ui/` + `build.gradle.kts` + `settings.gradle.kts` entry
- [ ] `InboxTheme`, `InboxListScreen`, `InboxDetailScreen`, `UnreadBadge`
- [ ] Screenshot tests or Compose preview verification

---

## Open / Deferred

| Item | Status |
|------|--------|
| Remote endpoint URL | Pending — placeholder used |
| Auth scheme for remote endpoint | Assumed unauthenticated (public) with profile query params |
| Whether to reuse analytics `OkHttp` or use `HttpURLConnection` | Decide when endpoint URL + scheme is confirmed |
| `messageId` null-safety fallback | UUID generation + warning log |
