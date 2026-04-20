# ConnectSphere – Social Media Mini Platform

> **Posts · Likes · Comments · Stories · Notifications · Search**
> Built on Java 21 + Spring Boot 3.2 + MySQL + Redis + RabbitMQ + Elasticsearch

---

## Project structure

```
connectsphere/
│
├── pom.xml                         ← Parent POM (dependencyManagement for all modules)
│
├── auth-service/           :8081   JWT, OAuth2 (GitHub/Google), profile management
├── post-service/           :8082   Post CRUD, news feed, visibility
├── comment-service/        :8083   Threaded comments + replies
├── like-service/           :8084   Polymorphic reactions (6 types)
├── follow-service/         :8085   Social graph, mutual follows, suggestions
├── notification-service/   :8086   In-app + email alerts via RabbitMQ
├── media-service/          :8087   S3 uploads, CDN, 24h stories
├── search-service/         :8088   Elasticsearch, hashtag indexing, trending
│
├── connectsphere-web/      :8080   Spring MVC + Thymeleaf UI layer
│
├── docker-compose.yml              Full local stack (MySQL, Redis, RabbitMQ, ES)
└── docker/mysql/init.sql           Creates all 8 databases automatically
```

Each service follows the **5-layer pattern**:
```
entity/         → JPA domain model
repository/     → Spring Data JPA interface
service/        → Business contract (interface)
serviceimpl/    → Business logic implementation
resource/       → REST controller (@RestController)
dto/            → Request / Response DTOs
config/         → Spring Security, RabbitMQ, Redis configs
security/       → JwtUtil, JwtFilter
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (JJWT 0.12.5) + OAuth2 |
| Database | MySQL 8.3 (one schema per service) |
| Cache | Redis 7 (feed cache, unread counts, sessions) |
| Messaging | RabbitMQ 3.13 (async events) |
| Search | Elasticsearch 8.13 |
| File Storage | AWS S3 + CloudFront CDN |
| Frontend | Thymeleaf + Bootstrap 5 |
| Real-time | WebSocket (STOMP) – live notification badge |
| Container | Docker + Docker Compose |
| API Docs | Swagger UI (springdoc-openapi 2.5.0) |

---

## Quick start (local)

### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

### 1 – Clone and build
```bash
git clone https://github.com/YOUR_USERNAME/connectsphere.git
cd connectsphere
mvn clean install -DskipTests
```

### 2 – Start infrastructure
```bash
docker-compose up -d mysql redis rabbitmq elasticsearch
```

### 3 – Run services (in order)
```bash
# Open a terminal per service OR use your IDE run configs
cd auth-service      && mvn spring-boot:run &
cd follow-service    && mvn spring-boot:run &
cd post-service      && mvn spring-boot:run &
cd comment-service   && mvn spring-boot:run &
cd like-service      && mvn spring-boot:run &
cd notification-service && mvn spring-boot:run &
cd media-service     && mvn spring-boot:run &
cd search-service    && mvn spring-boot:run &
cd connectsphere-web && mvn spring-boot:run &
```

### 4 – Access
| URL | Description |
|---|---|
| http://localhost:8080 | Main web application |
| http://localhost:8081/swagger-ui.html | Auth Service API docs |
| http://localhost:8082/swagger-ui.html | Post Service API docs |
| http://localhost:15672 | RabbitMQ Management UI |
| http://localhost:9200 | Elasticsearch |

---

## Service port map

| Service | Port |
|---|---|
| connectsphere-web | 8080 |
| auth-service | 8081 |
| post-service | 8082 |
| comment-service | 8083 |
| like-service | 8084 |
| follow-service | 8085 |
| notification-service | 8086 |
| media-service | 8087 |
| search-service | 8088 |

---

## RabbitMQ events

| Event | Published by | Consumed by |
|---|---|---|
| `user.created` | auth-service | notification-service |
| `post.published` | post-service | notification-service, search-service |
| `like.added` | like-service | notification-service, post-service |
| `comment.added` | comment-service | notification-service, post-service |
| `follow.added` | follow-service | notification-service |
| `story.expired` | media-service | search-service |

---

## Before you run

Replace these placeholder values in `application.properties` of each service:

- `YOUR_GITHUB_CLIENT_ID` / `YOUR_GITHUB_CLIENT_SECRET` → GitHub OAuth App
- `YOUR_GOOGLE_CLIENT_ID` / `YOUR_GOOGLE_CLIENT_SECRET` → Google Cloud Console
- `YOUR_AWS_ACCESS_KEY` / `YOUR_AWS_SECRET_KEY` → AWS IAM user
- `YOUR_EMAIL@gmail.com` / `YOUR_APP_PASSWORD` → Gmail SMTP app password
- `YOUR_DISTRIBUTION.cloudfront.net` → CloudFront distribution domain

> **Never commit real credentials.** Add `application-prod.properties` to `.gitignore`
> and use environment variables in Docker / Kubernetes.

---

## Databases created automatically

`docker/mysql/init.sql` creates all 8 schemas on first container start:
`cs_auth_db`, `cs_post_db`, `cs_comment_db`, `cs_like_db`,
`cs_follow_db`, `cs_notification_db`, `cs_media_db`, `cs_search_db`

Spring `ddl-auto=update` then creates tables from JPA entities on first boot.
