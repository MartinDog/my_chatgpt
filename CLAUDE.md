# CLAUDE.md

This file provides guidance for Claude Code when working with this codebase.

## Project Overview

My ChatGPT is a Spring Boot-based AI chatbot API server with RAG (Retrieval-Augmented Generation) support. It provides conversational AI capabilities powered by OpenAI's GPT API with vector search via ChromaDB.

## Tech Stack

- **Language:** Java 17+
- **Framework:** Spring Boot 3.2.1
- **Build Tool:** Gradle (Kotlin DSL)
- **Database:** PostgreSQL 16
- **Vector DB:** ChromaDB 0.4.22
- **AI API:** OpenAI (GPT-4o-mini, text-embedding-3-small)

## Build & Run Commands

```bash
# Run locally (requires PostgreSQL and ChromaDB running)
export OPENAI_API_KEY=sk-your-api-key
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
├── ai/           # OpenAI client and response wrappers
├── config/       # Spring configuration classes
├── controller/   # REST API endpoints
├── service/      # Business logic layer
├── entity/       # JPA entities (User, ChatSession, ChatMessage)
├── repository/   # Spring Data JPA repositories
├── dto/          # Request/Response DTOs
├── tool/         # AI function calling tool system
│   └── impl/     # Tool implementations (Calculator, CurrentTime, VectorSearch)
└── vectordb/     # ChromaDB client and search results
```

## Key Patterns

- **Layered Architecture:** Controller → Service → Repository/Client
- **Tool Registry Pattern:** Plugin-based tool system for AI function calling - implement `ChatTool` interface
- **Lombok:** Uses `@Data`, `@RequiredArgsConstructor`, `@Slf4j` throughout
- **Configuration:** Type-safe binding via `@ConfigurationProperties`

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/users/login` | Create/authenticate user |
| POST | `/api/sessions` | Create chat session |
| GET | `/api/sessions/user/{userId}` | List user's sessions |
| POST | `/api/chat` | Send chat message (with RAG) |
| GET | `/api/chat/history/{sessionId}` | Get conversation history |
| POST | `/api/files/upload` | Upload documents for RAG |
| POST | `/api/vectordb/search` | Search knowledge base |

## Environment Variables

Required:
- `OPENAI_API_KEY` - OpenAI API key

Optional (with defaults):
- `DB_HOST` (localhost), `DB_PORT` (5432), `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `CHROMA_HOST` (localhost), `CHROMA_PORT` (8000), `CHROMA_COLLECTION`
- `OPENAI_MODEL` (gpt-4o-mini)

## Code Conventions

- Package naming: `com.mychatgpt.[feature]`
- REST endpoints use plural nouns: `/api/sessions`, `/api/chat`
- Error messages are in Korean for user-facing responses
- Document chunking: 1000 chars with 200-char overlap
- Conversation context limited to last 20 messages

## Adding New AI Tools

1. Create a class in `src/main/java/com/mychatgpt/tool/impl/`
2. Implement the `ChatTool` interface
3. Annotate with `@Component`
4. Define tool schema in `getDefinition()` method

## Testing Notes

- Test directory exists but no tests are implemented yet
- Use `./gradlew test` to run tests
- Integration tests should use testcontainers for PostgreSQL and ChromaDB
