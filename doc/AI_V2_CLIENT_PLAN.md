# AI v2 Client Integration Plan

**Версия:** v1.2.0.20 | **Ветка:** feat/1.2.0.x | **Дата:** 2026-06-20

---

## Цель

Замена v1 AI реализации (OWL + Hermes/Orchestrator) на единый v2 API (`ChatWithAIV2`). Агенты создаются через gRPC API, поддерживаются инструменты (tool calling), 7 типов провайдеров.

---

## Инвентаризация: Что удаляется

### gRPC транспорт (~7400 LOC)
| Файл | Строк | Описание |
|------|-------|----------|
| `data/grpc/OwlGrpc.kt` | ~1100 | OWL streaming, settings, bot commands, notifications, marshallers |
| `data/grpc/HermesGrpc.kt` | ~1000 | Orchestrator streaming, agent CRUD, sessions, remote agents, marshallers |
| `data/grpc/AiChatGrpc.kt` | ~407 | Unified v1 AI chat (ChatWithAI), settings, history |

### Domain / UseCase слой (~690 LOC)
| Файл | Строк | Описание |
|------|-------|----------|
| `data/ai/AiModels.kt` | 68 | AiSource enum (OWL/HERMES), AiChatSession, AiChatMessage, AiStreamState |
| `data/ai/AiChatManager.kt` | 83 | SharedFlow/StateFlow для OWL и Hermes |
| `data/ai/OwlChatUseCase.kt` | 200 | OWL chat orchestration + settings |
| `data/ai/HermesChatUseCase.kt` | 245 | Hermes chat orchestration + agents + remote agents + tokens |
| `data/ai/AiDomainExtensions.kt` | 96 | Proto → domain mapping |
| `data/repository/HermesRepository.kt` | 207 | GrpcClient wrapper для Hermes agent CRUD |
| `data/models/HermesModel.kt` | 67 | HermesMessage, AgentInfo, AgentPreset, RemoteAgentInfo, HermesSession, OwlMessage |

### UI (~2600 LOC)
| Файл | Строк | Описание |
|------|-------|----------|
| `ui/owl/OwlChatActivity.kt` | ~467 | OWL chat UI + bot commands |
| `ui/owl/OwlChatViewModel.kt` | — | OWL ViewModel |
| `ui/owl/OwlSettingsActivity.kt` | — | OWL API key + model settings |
| `ui/hermes/HermesChatActivity.kt` | ~482 | Hermes orchestrator chat UI + mentions |
| `ui/hermes/HermesChatViewModel.kt` | — | Hermes ViewModel |
| `ui/hermes/HermesChatAdapter.kt` | — | Hermes message adapter |
| `ui/hermes/HermesCommandAdapter.kt` | — | Hermes command adapter |
| `ui/hermes/AgentListActivity.kt` | — | Agent list screen |
| `ui/hermes/AgentListViewModel.kt` | — | Agent list ViewModel |
| `ui/hermes/AgentListAdapter.kt` | — | Agent list adapter |
| `ui/hermes/AgentSettingsActivity.kt` | — | Agent create/edit screen |
| `ui/hermes/AgentSettingsBottomSheet.kt` | — | Agent settings sheet |
| `ui/widget/AIBottomSheet.kt` | ~267 | AI FAB bottom sheet (OWL + Hermes sections) |

### Layout XML (~10 файлов)
- `activity_owl_chat.xml`
- `activity_owl_settings.xml`
- `activity_hermes_chat.xml`
- `activity_agent_list.xml`
- `activity_agent_settings.xml`
- `bottom_sheet_agent_settings.xml`
- `widget_ai_bottom_sheet.xml`
- `item_hermes_message.xml`
- `item_agent_card.xml`
- `item_mention_agent.xml`

### AndroidManifest (6 entries)
- `HermesChatActivity`, `AgentListActivity`, `OwlChatActivity`, `OwlSettingsActivity`, `AgentSettingsActivity`, `RemoteAgentSettingsActivity`

### String resources (~44 строки)
- OWL, Hermes, agent-related строки в `values/strings.xml` и `values-ru/strings.xml`

---

## Инвентаризация: Что создаётся (v2)

### 1. Proto модели (`data/proto/`)

**Новые модели** (сервер proto поля определяют field numbers):

```
ChatWithAIV2Request   — session_id(1), message(2), images(3), agent_id(4), tool_calls(5)
ChatWithAIV2Response  — token(1), finished(2), error(3), agent_id(4), agent_name(5), tool_calls(6), has_rag_context(7), model_used(8), token_count(9)
ToolCallRequestV2     — id(1), name(2), arguments(3)
ToolCallV2            — id(1), name(2), arguments(3), result(4)

AgentInfoV2           — id(1), name(2), description(3), provider_type(4), model(5), system_prompt(6), tools_enabled(7), rag_enabled(8), is_preset(9), is_public(10), max_tokens(11), temperature(12), created_by(13), capabilities(14)
AgentCapabilitiesV2   — supports_images(1), supports_tools(2), supports_streaming(3), max_tokens(4)

CreateAIAgentRequest  — name(1), description(2), provider_type(3), provider_config(4), system_prompt(5), model(6), max_tokens(7), temperature(8), tools_enabled(9), tool_whitelist(10), rag_enabled(11), rag_config(12), rate_limit(13), is_public(14)
CreateAIAgentResponse — success(1), agent_id(2), error(3)

UpdateAIAgentRequest  — agent_id(1), name(2), description(3), provider_config(4), system_prompt(5), model(6), max_tokens(7), temperature(8), tools_enabled(9), tool_whitelist(10), rag_enabled(11), rag_config(12), rate_limit(13), is_public(14)
UpdateAIAgentResponse — success(1), error(2)

DeleteAIAgentRequest  — agent_id(1)
DeleteAIAgentResponse — success(1), error(2)

GetAIAgentRequest     — agent_id(1)
GetAIAgentResponse    — agent(1) [AgentInfoV2]

ListAIAgentsRequest   — include_public(1)
ListAIAgentsResponse  — agents(1) [repeated AgentInfoV2]

CloneAIAgentRequest   — agent_id(1), new_name(2)
CloneAIAgentResponse  — success(1), agent_id(2), error(3)

ListAIToolsRequest    — (пусто)
ListAIToolsResponse   — tools(1) [repeated ToolInfoV2]

ToolInfoV2            — name(1), description(2), parameters_schema(3), required_role(4)
```

### 2. Marshallers (`data/grpc/GrpcAIv2Marshallers.kt` — новый файл)

Новый файл со всеми marshallers для v2 AI. Следует паттерну из `GrpcMarshallers.kt`:
- Request marshallers: serialize all non-empty fields
- Response marshallers: parse by field number, skip unknown
- Nested messages (AgentInfoV2, ToolCallRequestV2, ToolCallV2): length-delimited parsing

### 3. gRPC транспорт (`data/grpc/GrpcAIv2Client.kt` — новый файл)

Заменяет OwlGrpc + HermesGrpc + AiChatGrpc. Единый модуль:

```
GrpcAIv2Client {
    // Streaming
    fun chatWithAIV2(request, scope, onResponse, onToolCall, onError) → chatId
    
    // Agent CRUD
    suspend fun createAgent(request) → CreateAIAgentResponse
    suspend fun updateAgent(request) → UpdateAIAgentResponse
    suspend fun deleteAgent(agentId) → Boolean
    suspend fun getAgent(agentId) → AgentInfoV2?
    suspend fun listAgents(includePublic) → List<AgentInfoV2>
    suspend fun cloneAgent(agentId, newName) → String?
    
    // Tools
    suspend fun listTools() → List<ToolInfoV2>
}
```

**Ключевой метод — chatWithAIV2:**
- Server streaming RPC: `messenger.AIService/ChatWithAIV2`
- Поддержка tool calling loop: если response содержит tool_calls → клиент выполняет → отправляет результат
- JWT auth через BearerTokenInterceptor (уже работает для всех RPC)
- Retry: 10 attempts, exponential backoff 3s→30s
- Stream timeout: 120s (reset on each token)

### 4. Domain модели (`data/ai/AiV2Models.kt` — новый файл)

```kotlin
enum class AiProviderType { OPENROUTER, LOCAL, MIMO, WEBHOOK, WEBSOCKET, SUBPROCESS, MCP }

data class AiV2Agent(
    val id: String,
    val name: String,
    val description: String,
    val providerType: AiProviderType,
    val model: String,
    val systemPrompt: String,
    val toolsEnabled: Boolean,
    val ragEnabled: Boolean,
    val isPreset: Boolean,
    val isPublic: Boolean,
    val maxTokens: Int,
    val temperature: Float,
    val createdBy: String,
    val capabilities: AiAgentCapabilities
)

data class AiAgentCapabilities(
    val supportsImages: Boolean,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val maxTokens: Int
)

data class AiV2ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    var result: String = ""
)

data class AiV2StreamState(
    val isStreaming: Boolean = false,
    val isTyping: Boolean = false,
    val tokens: List<String> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
    val agentId: String = "",
    val agentName: String = "",
    val toolCalls: List<AiV2ToolCall> = emptyList(),
    val hasRagContext: Boolean = false,
    val modelUsed: String = "",
    val tokenCount: Int = 0
)

data class AiV2Tool(
    val name: String,
    val description: String,
    val parametersSchema: String,
    val requiredRole: String
)
```

### 5. Domain extensions (`data/ai/AiV2DomainExtensions.kt` — новый файл)

Proto → domain mapping для всех v2 типов.

### 6. UseCase (`data/ai/AiV2ChatUseCase.kt` — заменяет OwlChatUseCase + HermesChatUseCase)

```kotlin
object AiV2ChatUseCase {
    // Chat с tool calling loop
    suspend fun chat(userId, sessionId, message, agentId, images, scope)
    
    // Agent CRUD
    suspend fun createAgent(request) → Result<AiV2Agent>
    suspend fun updateAgent(request) → Result<Boolean>
    suspend fun deleteAgent(agentId) → Result<Boolean>
    suspend fun getAgent(agentId) → AiV2Agent?
    suspend fun listAgents(includePublic) → List<AiV2Agent>
    suspend fun cloneAgent(agentId, newName) → Result<String>
    
    // Tools
    suspend fun listTools() → List<AiV2Tool>
}
```

**Tool calling loop** (ключевая логика):
```
chat() → stream tokens → если tool_calls:
    → показать UI "выполняю инструмент..."
    → отправить tool_calls результат обратно (сервер выполняет инструменты)
    → stream tokens → если tool_calls again → repeat (max 10 итераций)
    → finished → done
```

**Встроенные инструменты** — выполняются СЕРВЕРОМ:
| Инструмент | Описание | Кто выполняет |
|------------|----------|---------------|
| `search_messages` | Поиск сообщений | Сервер |
| `search_users` | Поиск пользователей | Сервер |
| `web_search` | Веб-поиск | Сервер |
| `web_fetch` | Загрузка URL | Сервер |
| `get_chat_info` | Метаданные чата | Сервер |

Клиент только отправляет `ToolCallV2` результат обратно в `ChatWithAIV2Request.tool_calls`.

### 7. Manager (`data/ai/AiV2ChatManager.kt` — заменяет AiChatManager)

Единый набор flows:
```kotlin
object AiV2ChatManager {
    val aiResponses: SharedFlow<AiV2StreamState>    // unified streaming
    val aiTyping: SharedFlow<Boolean>                // typing indicator
    val agents: StateFlow<List<AiV2Agent>>           // cached agent list
    val tools: StateFlow<List<AiV2Tool>>             // available tools
}
```

### 8. UI

#### 8a. AiV2ChatActivity — единый экран + нижние шторки
- Единый экран для всех типов AI чатов (simple, agent, pipeline)
- Тип чата определяется по `agent_id`
- Поддержка tool calling UI (показать "выполняю инструмент..." + анимация)
- Меншены агентов через `@agent_name`
- Выбор агента в шапке чата
- Поддержка изображений (multimodal)
- **Нижние шторки:**
  - ToolCallBottomSheet — показывает выполняемые инструменты
  - AgentInfoBottomSheet — информация об агенте (модель, capabilities)
  - AiSettingsBottomSheet — настройки чата (модель, temperature)

#### 8b. Замена AgentListActivity → AiV2AgentListActivity
- Список агентов (встроенные + пользовательские + публичные)
- Фильтр по провайдеру
- Клонирование агентов

#### 8c. Замена AgentSettingsActivity → AiV2AgentCreateEditActivity
- Создание/редактирование агента
- Выбор провайдера (7 типов)
- Настройка provider_config (JSON editor или формы)
- Включение/выключение tools и RAG

#### 8d. Обновление AIBottomSheet
- Убрать раздел OWL
- Единый раздел "AI Chat" с пресетами агентов
- Кнопка "Все агенты" → AiV2AgentListActivity
- Remote Agent → интегрирован как тип провайдера в v2

#### 8e. Обновление ChatListActivity
- `ChatListFABs.kt`: убратьOWL логику, единый AI FAB
- `ChatListNavigation.kt`: заменить owl/hermes навигацию на ai_v2

### 9. AndroidManifest
- Удалить: `OwlChatActivity`, `OwlSettingsActivity`
- Заменить: `HermesChatActivity` → `AiV2ChatActivity`
- Заменить: `AgentListActivity` → `AiV2AgentListActivity`
- Заменить: `AgentSettingsActivity` → `AiV2AgentCreateEditActivity`
- Оставить: `RemoteAgentSettingsActivity` (Remote Agent — отдельная система)

---

## Порядок выполнения

### Фаза 1: Proto + Marshallers (без UI)
1. Создать все proto модели v2
2. Создать `GrpcAIv2Marshallers.kt` со всеми marshallers
3. Создать `GrpcAIv2Client.kt` с chatWithAIV2 + agent CRUD + tools
4. Unit-тесты marshallers

### Фаза 2: Domain + UseCase
5. Создать `AiV2Models.kt` (domain модели)
6. Создать `AiV2DomainExtensions.kt` (proto → domain)
7. Создать `AiV2ChatUseCase.kt` (chat + tool calling loop + agent CRUD)
8. Создать `AiV2ChatManager.kt` (shared flows)
9. Unit-тесты для UseCase

### Фаза 3: UI
10. Создать `AiV2ChatActivity.kt` (единый AI чат)
11. Создать `AiV2ChatViewModel.kt`
12. Создать `AiV2AgentListActivity.kt` (список агентов)
13. Создать `AiV2AgentCreateEditActivity.kt` (создание/редактирование)
14. Обновить `AIBottomSheet.kt` (единый AI раздел)
15. Обновить `ChatListFABs.kt` + `ChatListNavigation.kt`

### Фаза 4: Cleanup
16. Удалить v1 файлы (OwlGrpc, HermesGrpc, AiChatGrpc, OwlChatUseCase, HermesChatUseCase, AiChatManager, AiModels, AiDomainExtensions, HermesRepository, HermesModel)
17. Удалить v1 UI (OwlChat*, HermesChat*, AgentList*, AgentSettings*)
18. **Оставить:** RemoteAgent* файлы (интегрированы в v2 как провайдер)
19. Удалить v1 layouts + strings
20. Обновить AndroidManifest
21. Удалить v1 proto модели (OwlRequestProto, OrchestratorRequestProto, etc.)
22. `./gradlew assembleDebug` — проверка сборки

### Фаза 5: Тесты
22. Unit-тесты для marshallers
23. Unit-тесты для AiV2ChatUseCase (tool calling loop)
24. Unit-тесты для AiV2ChatManager

---

## Зависимости

| Компонент | Зависит от | Статус |
|-----------|------------|--------|
| GrpcAIv2Marshallers | Server proto field order | Нужно уточнить у сервера |
| GrpcAIv2Client | BearerTokenInterceptor (уже есть) | ✅ |
| AiV2ChatUseCase | GrpcMessageClient (search_messages) | ✅ |
| AiV2ChatUseCase | GrpcChatAuxClient (search_users) | ✅ |
| AiV2ChatUseCase | GrpcChatClient (get_chat_info) | ✅ |
| UI | GrpcAIv2Client + AiV2ChatUseCase | Phase 1-2 |

---

## Решения (утверждено)

| Вопрос | Решение |
|--------|---------|
| Proto field order | Использовать из `AI_V2_CLIENT_INTEGRATION.md` (field numbers из серверной docs) |
| Tool execution | **Сервер выполняет** все встроенные инструменты. Клиент просто отправляет tool_calls результат обратно |
| UI design | **Единый AiV2ChatActivity + нижние шторки** (type-specific bottom sheets) |
| Remote Agent | **Интегрировать в v2** — Remote Agent становится типом провайдера в v2 agent system |
| Backward compat | **Чистый старт** — старые чаты OWL/Hermes не мигрируются |

---

## Оценка

| Фаза | Оценка |
|------|--------|
| Phase 1: Proto + Marshallers | ~4-5h |
| Phase 2: Domain + UseCase | ~3-4h |
| Phase 3: UI (включая нижние шторки) | ~6-7h |
| Phase 4: Cleanup v1 | ~1-2h |
| Phase 5: Tests | ~2-3h |
| Remote Agent интеграция в v2 | ~2-3h |
| **Итого** | **~18-24h** |

---

## Правила (обязательно)

1. **НЕ компилировать Android на сервере** (OOM)
2. **НЕ деплоить на prod** без явного указания
3. userId (UUID) — всегда как ключ, НЕ username
4. Все новые строки ОДНОВРЕМЕННО в values/strings.xml + values-ru/strings.xml
5. getString() НЕ в полях Activity — только в методах
6. Все ошибки через `ErrorHandler.handle()` — НЕ `Log.e`
7. v2 server only — никаких v1 fallbacks
8. Перед коммитом: `./gradlew assembleDebug`
9. NE bump'ать версию — bump делает только пользователь
10. Marshallers field order: server proto определяет field numbers
11. Do not add features without explicit request
12. Do not refactor working code without explicit request
