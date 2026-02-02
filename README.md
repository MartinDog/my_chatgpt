# My ChatGPT - AI 챗봇 API 서버

Spring Boot 기반의 개인용 AI 챗봇 API 서버입니다. OpenAI API를 활용하여 대화형 AI 서비스를 제공하며, Vector DB를 통한 RAG(Retrieval-Augmented Generation) 기능을 지원합니다.

## 주요 기능

- **대화 관리**: 세션 기반 대화 관리 및 히스토리 저장
- **RAG 지원**: ChromaDB를 활용한 문서 검색 및 컨텍스트 기반 답변
- **파일 업로드**: PDF, Word 등 다양한 형식의 파일 업로드 및 텍스트 추출
- **커스텀 시스템 프롬프트**: 세션별 AI 성격/역할 커스터마이징
- **Tool 확장**: 계산기, 시간 조회, Vector 검색 등 AI가 사용할 수 있는 도구 제공
- **사용자 관리**: 사용자별 데이터 격리

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Spring Boot 3.2.1, Java 17+ |
| **Database** | PostgreSQL 16 |
| **Vector DB** | ChromaDB 0.4.22 |
| **AI** | OpenAI API (GPT-4o-mini, text-embedding-3-small) |
| **파일 처리** | Apache Tika 2.9.1 |ㄱ
| **배포** | Docker, Docker Compose |

## 시작하기

### 사전 요구사항

- Docker & Docker Compose
- OpenAI API Key

### 설치 및 실행

1. **저장소 클론**
   ```bash
   git clone <repository-url>
   cd my_chatgpt
   ```

2. **환경 변수 설정**
   ```bash
   cp .env.example .env
   ```

   `.env` 파일을 열어 OpenAI API 키를 설정합니다:
   ```
   OPENAI_API_KEY=sk-your-actual-api-key-here
   OPENAI_MODEL=gpt-4o-mini
   ```

3. **Docker Compose로 실행**
   ```bash
   docker compose up -d
   ```

4. **서비스 확인**
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

1. **인프라 서비스만 실행**
   ```bash
   docker compose up -d postgres chromadb
   ```

2. **애플리케이션 실행**
   ```bash
   # 환경 변수 설정
   export OPENAI_API_KEY=sk-your-api-key

   # Gradle로 실행
   ./gradlew bootRun
   ```

## API 명세

### 사용자 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/users/login` | 사용자 로그인/생성 |
| GET | `/api/users/{userId}` | 사용자 조회 |

**요청 예시:**
```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"userId": "my-user-id"}'
```

### 세션 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/sessions` | 세션 생성 |
| GET | `/api/sessions/{sessionId}` | 세션 조회 |
| GET | `/api/sessions/user/{userId}` | 사용자의 모든 세션 조회 |
| PUT | `/api/sessions/{sessionId}` | 세션 수정 (제목, 시스템 프롬프트) |
| DELETE | `/api/sessions/{sessionId}` | 세션 삭제 |

**세션 생성 예시:**
```bash
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "my-user-id",
    "title": "코딩 도우미",
    "systemPrompt": "당신은 친절한 프로그래밍 튜터입니다. 코드 예제와 함께 설명해주세요."
  }'
```

### 채팅

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/chat` | 메시지 전송 |
| GET | `/api/chat/history/{sessionId}` | 대화 히스토리 조회 |

**채팅 예시:**
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

**파일 업로드 예시:**
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@document.pdf" \
  -F "userId=my-user-id"
```

**지원 파일 형식:** PDF, Word(.docx), 텍스트(.txt), HTML 등

### Vector DB 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/vectordb/documents` | 문서 수동 추가 |
| POST | `/api/vectordb/search` | 문서 검색 |
| DELETE | `/api/vectordb/documents` | 문서 삭제 (ID 기반) |
| DELETE | `/api/vectordb/users/{userId}` | 사용자의 모든 문서 삭제 |

**문서 검색 예시:**
```bash
curl -X POST http://localhost:8080/api/vectordb/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Python 함수 정의 방법",
    "userId": "my-user-id",
    "nResults": 5
  }'
```

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                      Client (Frontend)                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Controllers │  │  Services   │  │       Tools         │  │
│  │             │  │             │  │  - Calculator       │  │
│  │ - Chat      │  │ - Chat      │  │  - CurrentTime      │  │
│  │ - Session   │──│ - Session   │──│  - VectorSearch     │  │
│  │ - File      │  │ - File      │  │                     │  │
│  │ - VectorDB  │  │ - VectorDB  │  └─────────────────────┘  │
│  │ - User      │  │ - User      │                           │
│  └─────────────┘  └─────────────┘                           │
└─────────────────────────────────────────────────────────────┘
         │                  │                    │
         ▼                  ▼                    ▼
┌─────────────┐    ┌─────────────┐      ┌─────────────┐
│ PostgreSQL  │    │  ChromaDB   │      │ OpenAI API  │
│             │    │ (Vector DB) │      │             │
│ - Users     │    │             │      │ - Chat      │
│ - Sessions  │    │ - Documents │      │ - Embedding │
│ - Messages  │    │ - Embeddings│      │             │
└─────────────┘    └─────────────┘      └─────────────┘
```

## 사용 가능한 AI Tools

AI가 대화 중 자동으로 호출할 수 있는 도구들입니다:

| Tool | 설명 |
|------|------|
| `calculator` | 사칙연산 (더하기, 빼기, 곱하기, 나누기) |
| `current_time` | 현재 시간 조회 |
| `vector_search` | 사용자의 지식 베이스에서 관련 정보 검색 |

## 프로젝트 구조

```
my_chatgpt/
├── src/main/java/com/mychatgpt/
│   ├── MyChatGptApplication.java    # 메인 클래스
│   ├── ai/                          # OpenAI 클라이언트
│   ├── config/                      # 설정 클래스
│   ├── controller/                  # REST API 컨트롤러
│   ├── dto/                         # 데이터 전송 객체
│   ├── entity/                      # JPA 엔티티
│   ├── repository/                  # JPA 리포지토리
│   ├── service/                     # 비즈니스 로직
│   ├── tool/                        # AI Tools
│   │   ├── ChatTool.java           # Tool 인터페이스
│   │   ├── ToolDefinition.java     # Tool 정의
│   │   ├── ToolRegistry.java       # Tool 레지스트리
│   │   └── impl/                   # Tool 구현체
│   └── vectordb/                    # ChromaDB 클라이언트
├── src/main/resources/
│   └── application.yml              # 애플리케이션 설정
├── Dockerfile                       # Docker 빌드 설정
├── docker-compose.yml               # Docker Compose 설정
├── build.gradle.kts                 # Gradle 빌드 설정
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
| `OPENAI_API_KEY` | - | OpenAI API 키 (필수) |
| `OPENAI_MODEL` | gpt-4o-mini | 사용할 GPT 모델 |
| `FILE_UPLOAD_DIR` | /app/uploads | 파일 업로드 경로 |

## 새로운 Tool 추가하기

1. `ChatTool` 인터페이스를 구현하는 클래스 생성:

```java
@Component
public class MyCustomTool implements ChatTool {

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            "my_tool",
            "도구 설명",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "param1", Map.of("type", "string", "description", "파라미터 설명")
                ),
                "required", List.of("param1")
            )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        // 도구 로직 구현
        return "결과";
    }
}
```

2. `@Component` 어노테이션으로 Spring Bean 등록 시 자동으로 Tool Registry에 추가됩니다.

## 라이선스

MIT License

## 기여하기

이슈 및 PR을 환영합니다!
