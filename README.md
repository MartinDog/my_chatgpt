# My ChatGPT - AI 챗봇 API 서버

Spring Boot + Spring AI 기반의 AI 챗봇 API 서버입니다. Ollama(qwen3:30b)를 활용한 대화형 AI 서비스와 ChromaDB를 통한 RAG(Retrieval-Augmented Generation) 기능을 제공합니다. 회사 Knowledge Base(YouTrack 이슈, Confluence 문서)를 벡터DB에 저장하여 업무 맥락 기반 답변을 지원합니다.

## 주요 기능

- **대화 관리**: 세션 기반 대화 관리 및 히스토리 저장
- **RAG 지원**: ChromaDB + Spring AI QuestionAnswerAdvisor를 활용한 문서 검색 및 컨텍스트 기반 답변
- **Knowledge Base**: YouTrack 이슈(xlsx)와 Confluence 문서(HTML)를 벡터DB에 일괄 저장/검색
- **파일 업로드**: PDF, Word 등 다양한 형식의 파일 업로드 및 텍스트 추출
- **AI Function Calling**: Spring AI `@Tool` 기반 도구 시스템 (계산기, 시간 조회, 벡터 검색, Knowledge Base 검색)
- **관련도 자동 저장**: AI 응답의 관련도 점수(relevance score)를 평가하여 70점 이상 대화를 벡터DB에 자동 저장
- **커스텀 시스템 프롬프트**: 세션별 AI 성격/역할 커스터마이징
- **사용자 관리**: 사용자별 데이터 격리

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Spring Boot 3.3.5, Java 17+ |
| **AI Framework** | Spring AI 1.0.3 |
| **AI Model** | Ollama (qwen3:30b 채팅, bge-m3 임베딩) |
| **Database** | PostgreSQL 16 (pgvector) |
| **Vector DB** | ChromaDB 0.6.3 |
| **문서 파싱** | Apache Tika 2.9.1, Apache POI 5.2.5 (xlsx), Jsoup 1.17.2 (HTML) |
| **배포** | Docker, Docker Compose |

## 시작하기

### 사전 요구사항

- Docker & Docker Compose
- (로컬 개발 시) Ollama 설치 및 모델 다운로드

### Docker Compose로 실행 (권장)

```bash
git clone <repository-url>
cd my_chatgpt
docker compose up -d
```

Docker Compose는 PostgreSQL, ChromaDB, Ollama, Spring Boot 앱을 한 번에 실행합니다.
Ollama 컨테이너가 시작된 후 모델을 다운로드합니다:

```bash
docker exec -it mychatgpt-ollama ollama pull qwen3:30b
docker exec -it mychatgpt-ollama ollama pull bge-m3
```

서비스 확인:
```bash
# 로그 확인
docker compose logs -f app

# 헬스 체크
curl http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"userId": "test-user"}'
```

### 로컬 개발 환경

Docker 없이 로컬에서 개발하려면:

1. **인프라 서비스 실행**
   ```bash
   docker compose up -d postgres chromadb
   ```

2. **Ollama 설치 및 모델 다운로드**
   ```bash
   # macOS
   brew install ollama
   ollama pull qwen3:30b
   ollama pull bge-m3
   ```

3. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

## API 명세

### 사용자 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/users/login` | 사용자 로그인/생성 |

```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"userId": "my-user-id"}'
```

### 세션 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/sessions` | 세션 생성 |
| GET | `/api/sessions/user/{userId}` | 사용자의 모든 세션 조회 |

```bash
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "my-user-id",
    "title": "코딩 도우미",
    "systemPrompt": "당신은 친절한 프로그래밍 튜터입니다."
  }'
```

### 채팅

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/chat` | 메시지 전송 (RAG + Tool 자동 호출) |
| GET | `/api/chat/history/{sessionId}` | 대화 히스토리 조회 |

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "your-session-id",
    "userId": "my-user-id",
    "message": "안녕하세요! Python으로 Hello World를 출력하는 방법을 알려주세요."
  }'
```

### 파일 업로드

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/files/upload` | 파일 업로드 (텍스트 추출 후 Vector DB 저장) |

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@document.pdf" \
  -F "userId=my-user-id"
```

지원 파일 형식: PDF, Word(.docx), 텍스트(.txt), HTML 등

### Vector DB 검색

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/vectordb/search` | 사용자의 문서 검색 |

```bash
curl -X POST http://localhost:8080/api/vectordb/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Python 함수 정의 방법",
    "userId": "my-user-id",
    "nResults": 5
  }'
```

### Knowledge Base (YouTrack + Confluence)

회사의 YouTrack 이슈와 Confluence 문서를 벡터DB에 저장하여 AI가 업무 맥락을 이해하고 답변할 수 있도록 합니다.

#### YouTrack 이슈 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/knowledge-base/upload` | YouTrack xlsx 파일 업로드 |
| PUT | `/api/knowledge-base/issues` | 단건 이슈 upsert (JSON) |
| DELETE | `/api/knowledge-base/issues/{id}` | 단건 이슈 삭제 |
| DELETE | `/api/knowledge-base/all` | 모든 YouTrack 데이터 삭제 |

```bash
# YouTrack export xlsx 업로드
curl -X POST http://localhost:8080/api/knowledge-base/upload \
  -F "file=@youtrack_export.xlsx"

# 단건 이슈 upsert
curl -X PUT http://localhost:8080/api/knowledge-base/issues \
  -H "Content-Type: application/json" \
  -d '{"id":"PATALK-1246", "title":"배너 적용", "body":"...", "comments":"..."}'
```

#### Confluence 문서 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/knowledge-base/upload-html` | Confluence HTML 파일 업로드 |
| POST | `/api/knowledge-base/ingest-html` | 서버 디렉토리에서 HTML 일괄 저장 |
| POST | `/api/knowledge-base/ingest-directory` | 디렉토리에서 xlsx+html 모두 저장 |
| DELETE | `/api/knowledge-base/confluence/{id}` | 단건 Confluence 문서 삭제 |
| DELETE | `/api/knowledge-base/confluence/all` | 모든 Confluence 데이터 삭제 |

```bash
# Confluence HTML 파일 업로드
curl -X POST http://localhost:8080/api/knowledge-base/upload-html \
  -F "files=@doc1.html" -F "files=@doc2.html"

# 서버 디렉토리에서 일괄 저장
curl -X POST "http://localhost:8080/api/knowledge-base/ingest-directory?path=/path/to/exports"
```

#### Knowledge Base 검색

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/knowledge-base/search` | YouTrack 이슈 검색 |
| GET | `/api/knowledge-base/search/confluence` | Confluence 문서 검색 |
| GET | `/api/knowledge-base/search/all` | 전체 Knowledge Base 통합 검색 |

```bash
curl "http://localhost:8080/api/knowledge-base/search/all?query=배너%20적용&nResults=5"
```

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (Frontend)                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Spring Boot Application                     │
│                                                                 │
│  ┌───────────────┐  ┌─────────────────┐  ┌───────────────────┐ │
│  │  Controllers   │  │    Services     │  │  AI Tools (@Tool) │ │
│  │               │  │                 │  │                   │ │
│  │ - Chat        │  │ - Chat          │  │ - Calculator      │ │
│  │ - Session     │──│ - Session       │──│ - CurrentTime     │ │
│  │ - File        │  │ - File          │  │ - VectorSearch    │ │
│  │ - KnowledgeBase│ │ - KnowledgeBase │  │ - KBSearch        │ │
│  │ - VectorDB    │  │ - VectorDB      │  └───────────────────┘ │
│  │ - User        │  │ - User          │                        │
│  └───────────────┘  │ - HtmlParser    │  ┌───────────────────┐ │
│                     │ - ExcelParser   │  │  Spring AI        │ │
│                     └─────────────────┘  │  ChatClient +     │ │
│                                          │  QA Advisor (RAG) │ │
│                                          └───────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
         │                  │                       │
         ▼                  ▼                       ▼
┌─────────────┐    ┌───────────────┐       ┌──────────────┐
│ PostgreSQL  │    │   ChromaDB    │       │    Ollama    │
│  (pgvector) │    │  (Vector DB)  │       │              │
│             │    │               │       │ - qwen3:30b  │
│ - Users     │    │ - Documents   │       │   (Chat)     │
│ - Sessions  │    │ - Embeddings  │       │ - bge-m3     │
│ - Messages  │    │ - Knowledge   │       │   (Embedding)│
└─────────────┘    └───────────────┘       └──────────────┘
```

## AI Tools

AI가 대화 중 자동으로 호출할 수 있는 도구들입니다 (Spring AI `@Tool` 어노테이션 기반):

| Tool | 설명 |
|------|------|
| `calculate` | 사칙연산 (더하기, 빼기, 곱하기, 나누기) |
| `getCurrentTime` | 현재 날짜와 시간 조회 (타임존 지정 가능) |
| `vectorSearch` | 사용자의 지식 베이스에서 관련 정보 검색 |
| `knowledgeBaseSearch` | 회사 Knowledge Base (YouTrack, Confluence) 검색 |

## 프로젝트 구조

```
my_chatgpt/
├── src/main/java/com/mychatgpt/
│   ├── MyChatGptApplication.java    # 메인 클래스
│   ├── ai/                          # 임베딩 서비스 (EmbeddingService 인터페이스 + Ollama 구현체)
│   ├── config/                      # 설정 (ChatClient, ChromaDB, CORS, 예외처리)
│   ├── controller/                  # REST API 컨트롤러
│   ├── dto/                         # DTO (ChatRequest, AiChatResponse, YouTrackIssueDto, ConfluenceDocumentDto)
│   ├── entity/                      # JPA 엔티티 (User, ChatSession, ChatMessage)
│   ├── repository/                  # JPA 리포지토리
│   ├── service/                     # 비즈니스 로직 (Chat, KnowledgeBase, VectorDB, File, HtmlParser, ExcelParser)
│   ├── tool/impl/                   # AI Tools (Calculator, CurrentTime, VectorSearch, KnowledgeBaseSearch)
│   └── vectordb/                    # ChromaDB 클라이언트
├── src/main/resources/
│   └── application.yml              # 애플리케이션 설정
├── Dockerfile
├── docker-compose.yml               # PostgreSQL + ChromaDB + Ollama + App
├── build.gradle.kts
└── README.md
```

## 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `DB_HOST` | localhost | PostgreSQL 호스트 |
| `DB_PORT` | 5432 | PostgreSQL 포트 |
| `DB_NAME` | mychatgpt | 데이터베이스 이름 |
| `DB_USERNAME` | mychatgpt | DB 사용자명 |
| `DB_PASSWORD` | mychatgpt | DB 비밀번호 |
| `CHROMA_HOST` | localhost | ChromaDB 호스트 |
| `CHROMA_PORT` | 8000 | ChromaDB 포트 |
| `CHROMA_COLLECTION` | mychatgpt | ChromaDB 컬렉션 이름 |
| `OLLAMA_HOST` | localhost | Ollama 호스트 |
| `OLLAMA_PORT` | 11434 | Ollama 포트 |
| `OLLAMA_CHAT_MODEL` | qwen3:30b | 채팅용 LLM 모델 |
| `OLLAMA_EMBEDDING_MODEL` | bge-m3 | 임베딩 모델 |
| `FILE_UPLOAD_DIR` | /app/uploads | 파일 업로드 경로 |

## 새로운 Tool 추가하기

Spring AI의 `@Tool` 어노테이션을 사용하여 AI가 호출할 수 있는 새 도구를 추가할 수 있습니다:

1. `tool/impl/` 에 새 클래스 생성:

```java
@Component
@RequiredArgsConstructor
public class MyCustomTools {

    @Tool(description = "도구에 대한 설명을 작성합니다.")
    public String myToolMethod(
            @ToolParam(description = "파라미터 설명") String param1,
            @ToolParam(description = "선택 파라미터", required = false) Integer param2) {
        // 도구 로직 구현
        return "결과";
    }
}
```

2. `ChatClientConfig.java`에 등록:

```java
@Bean
public ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore,
                              MyCustomTools myCustomTools, /* 기존 tools... */) {
    return ChatClient.builder(chatModel)
            .defaultTools(myCustomTools, /* 기존 tools... */)
            .defaultAdvisors(/* ... */)
            .build();
}
```

## 라이선스

MIT License
