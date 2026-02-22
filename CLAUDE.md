# CLAUDE.md

This file provides guidance for Claude Code when working with this codebase.

## Project Overview

My ChatGPT is a Spring Boot-based AI chatbot API server with RAG (Retrieval-Augmented Generation) support. It provides conversational AI capabilities powered by Ollama (qwen3:30b) with vector search via ChromaDB. Includes a company Knowledge Base system for ingesting YouTrack issues and Confluence documents.

## Tech Stack

- **Language:** Java 17+
- **Framework:** Spring Boot 3.3.5
- **AI Framework:** Spring AI 1.0.3 (Ollama + ChromaDB vector store)
- **Build Tool:** Gradle (Kotlin DSL)
- **Database:** PostgreSQL 16
- **Vector DB:** ChromaDB (via Spring AI `spring-ai-starter-vector-store-chroma`)
- **AI Model:** Ollama (qwen3:30b for chat, bge-m3 for embedding - 1024 dimensions)
- **Document Parsing:** Apache Tika, Apache POI (xlsx), Jsoup (HTML)

## Build & Run Commands

```bash
# Run locally (requires PostgreSQL, ChromaDB, and Ollama running)
# ollama pull qwen3:30b && ollama pull bge-m3
./gradlew bootRun

# Run with Docker (full stack)
docker compose up -d

# Build JAR
./gradlew bootJar

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

## Project Structure

```
src/main/java/com/mychatgpt/
├── MyChatGptApplication.java       # Spring Boot main class
├── ai/                              # Embedding abstraction layer
│   ├── EmbeddingService.java        #   Interface for embedding
│   └── OllamaEmbeddingService.java  #   Ollama bge-m3 implementation (Spring AI EmbeddingModel)
├── config/                          # Spring configuration
│   ├── ChatClientConfig.java        #   Spring AI ChatClient bean (tools + QuestionAnswerAdvisor)
│   ├── ChromaDbConfig.java          #   ChromaDB connection config
│   ├── GlobalExceptionHandler.java  #   Global exception handler
│   └── WebConfig.java               #   CORS and web config
├── controller/                      # REST API endpoints
│   ├── ChatController.java          #   Chat API (/api/chat)
│   ├── ChatSessionController.java   #   Session management (/api/sessions)
│   ├── FileController.java          #   File upload for RAG (/api/files)
│   ├── KnowledgeBaseController.java #   Knowledge Base CRUD (/api/knowledge-base)
│   ├── UserController.java          #   User management (/api/users)
│   └── VectorDbController.java      #   Vector DB operations (/api/vectordb)
├── service/                         # Business logic
│   ├── ChatService.java             #   Chat logic (Spring AI ChatClient, relevance scoring)
│   ├── ChatSessionService.java      #   Session CRUD
│   ├── ConfluenceHtmlParser.java    #   HTML parsing for Confluence exports (Jsoup)
│   ├── FileService.java             #   File upload and text extraction
│   ├── KnowledgeBaseService.java    #   YouTrack/Confluence ingestion & search
│   ├── UserService.java             #   User CRUD
│   ├── VectorDbService.java         #   General vector DB operations
│   └── YouTrackExcelParser.java     #   XLSX parsing for YouTrack exports (Apache POI)
├── entity/                          # JPA entities
│   ├── User.java
│   ├── ChatSession.java
│   └── ChatMessage.java
├── repository/                      # Spring Data JPA repositories
│   ├── UserRepository.java
│   ├── ChatSessionRepository.java
│   └── ChatMessageRepository.java
├── dto/                             # Request/Response DTOs
│   ├── AiChatResponse.java          #   Chat response with relevance score
│   ├── ChatRequest.java             #   Chat request DTO
│   ├── ConfluenceDocumentDto.java   #   Confluence document DTO (with toVectorDocument())
│   └── YouTrackIssueDto.java        #   YouTrack issue DTO (with toVectorDocument())
├── tool/impl/                       # Spring AI Function Calling tools (@Tool annotation)
│   ├── CalculatorTools.java         #   Basic arithmetic operations
│   ├── CurrentTimeTools.java        #   Current date/time lookup
│   ├── KnowledgeBaseSearchTools.java#   Knowledge Base search (YouTrack + Confluence)
│   └── VectorSearchTools.java       #   User-scoped vector search
└── vectordb/                        # ChromaDB client layer
    ├── ChromaDbClient.java          #   Low-level ChromaDB REST client
    └── VectorSearchResult.java      #   Search result wrapper
```

## Key Patterns

- **Layered Architecture:** Controller → Service → Repository/Client
- **Spring AI ChatClient:** Central `ChatClient` bean configured with tools and `QuestionAnswerAdvisor` for RAG
- **Spring AI Tool System:** Tools use `@Tool` and `@ToolParam` annotations (registered via `ChatClient.defaultTools()`)
- **Dual Vector DB Access:** Spring AI `VectorStore` for automatic RAG advisor + `ChromaDbClient` for custom operations (knowledge base, filtered search)
- **Relevance Scoring:** AI responses include `<relevance_score>` tag; conversations scoring ≥70 are auto-stored in vector DB
- **Batch Upsert:** Knowledge Base ingestion processes documents in batches of 50
- **Lombok:** Uses `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` throughout
- **Configuration:** `application.yml` with environment variable overrides

## API Endpoints

### Chat & Session

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/users/login` | Create/authenticate user |
| POST | `/api/sessions` | Create chat session |
| GET | `/api/sessions/user/{userId}` | List user's sessions |
| POST | `/api/chat` | Send chat message (with RAG + tools) |
| GET | `/api/chat/history/{sessionId}` | Get conversation history |

### File Upload & Vector Search

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/files/upload` | Upload documents for RAG |
| POST | `/api/vectordb/search` | Search user's knowledge base |

### Knowledge Base (YouTrack + Confluence)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/knowledge-base/upload` | Upload YouTrack xlsx |
| POST | `/api/knowledge-base/upload-html` | Upload Confluence HTML files |
| POST | `/api/knowledge-base/ingest-html` | Ingest HTML from server directory |
| POST | `/api/knowledge-base/ingest-directory` | Ingest all files (xlsx+html) from directory |
| PUT | `/api/knowledge-base/issues` | Upsert single YouTrack issue (JSON) |
| GET | `/api/knowledge-base/search` | Search YouTrack issues |
| GET | `/api/knowledge-base/search/confluence` | Search Confluence docs |
| GET | `/api/knowledge-base/search/all` | Search all knowledge base |
| DELETE | `/api/knowledge-base/issues/{id}` | Delete single issue |
| DELETE | `/api/knowledge-base/all` | Delete all YouTrack data |
| DELETE | `/api/knowledge-base/confluence/{id}` | Delete single Confluence doc |
| DELETE | `/api/knowledge-base/confluence/all` | Delete all Confluence data |

## Environment Variables

Optional (with defaults):
- `DB_HOST` (localhost), `DB_PORT` (5432), `DB_NAME` (mychatgpt), `DB_USERNAME` (mychatgpt), `DB_PASSWORD` (mychatgpt)
- `CHROMA_HOST` (localhost), `CHROMA_PORT` (8000), `CHROMA_COLLECTION` (mychatgpt)
- `OLLAMA_HOST` (localhost), `OLLAMA_PORT` (11434), `OLLAMA_CHAT_MODEL` (qwen3:30b), `OLLAMA_EMBEDDING_MODEL` (bge-m3)
- `FILE_UPLOAD_DIR` (/app/uploads)

## Code Conventions

- Package naming: `com.mychatgpt.[feature]`
- REST endpoints use plural nouns: `/api/sessions`, `/api/chat`
- Error messages are in Korean for user-facing responses
- Document chunking: 1000 chars with 200-char overlap
- Conversation context limited to last 20 messages
- Knowledge Base batch size: 50 documents per upsert

## Adding New AI Tools

1. Create a class in `src/main/java/com/mychatgpt/tool/impl/`
2. Annotate with `@Component`
3. Create methods annotated with `@Tool(description = "...")` and `@ToolParam`
4. Register in `ChatClientConfig.chatClient()` via `.defaultTools()`

## Testing Notes

- Test directory exists but no tests are implemented yet
- Use `./gradlew test` to run tests
- Integration tests should use testcontainers for PostgreSQL and ChromaDB
