# ConnectSphere

ConnectSphere is a full-stack social platform built with Spring Boot microservices and a React frontend. The backend is split into independently owned services for authentication, posts, comments, reactions, follows, notifications, media, search, API routing, and service discovery.

## Project Structure

```text
connectsphere/
  api-gateway/              Spring Cloud Gateway entry point
  eureka-server/            Eureka service registry
  auth-service/             Registration, login, JWT, profiles, admin users
  post-service/             Posts, feeds, visibility, counters
  comment-service/          Comments and replies
  like-service/             Reactions for posts, comments, and stories
  follow-service/           Follow graph, followers, following, suggestions
  notification-service/     Notifications and content reports
  media-service/            Media uploads and stories
  search-service/           Post search, user search proxy, hashtags
  connectsphere-frontend/   React client
  docker/                   Local Docker support
  docker-compose.yml        Full local stack
  pom.xml                   Backend Maven multi-module build
```

## Backend Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| api-gateway | 8080 | Routes `/api/v1/**` requests to backend services |
| auth-service | 8081 | Auth, JWT, users, profiles, admin user operations |
| post-service | 8082 | Posts, feed, visibility, post counters |
| comment-service | 8083 | Comments, replies, comment counts |
| like-service | 8084 | Likes and reaction summaries |
| follow-service | 8085 | Follows, followers, following, suggestions |
| notification-service | 8086 | Notifications and moderation reports |
| media-service | 8087 | File uploads, media serving, stories |
| search-service | 8088 | Search index, hashtags, user search proxy |
| eureka-server | 8761 | Service registry |

## Infrastructure

Docker Compose starts:

- MySQL on host port `3307`
- Redis on `6379`
- RabbitMQ on `5672`, management UI on `15672`
- Elasticsearch on `9200`
- Eureka on `8761`
- API gateway on `8080`
- Backend services on `8081` through `8088`

Each service owns its own database schema. Cross-service links such as `authorId`, `postId`, `targetId`, and `recipientId` are logical API references, not shared database foreign keys.

## Requirements

- Java 21
- Maven 3.9+ for local backend builds
- Node.js 18+ or 20+ for local frontend work
- Docker Desktop for the containerized stack

## Quick Start With Docker

From this directory:

```powershell
docker compose up -d --build
```

Open:

- API gateway: `http://localhost:8080`
- Eureka dashboard: `http://localhost:8761`
- RabbitMQ management: `http://localhost:15672` with `guest` / `guest`

The frontend service is behind the `frontend` Compose profile. To include it:

```powershell
docker compose --profile frontend up -d --build
```

Then open:

- Frontend: `http://localhost:3000`

Stop everything:

```powershell
docker compose down
```

## Local SonarQube Analysis

SonarQube is optional and runs behind Docker Compose profiles, so the normal app stack and existing service images are unchanged.

Start SonarQube and its PostgreSQL database:

```powershell
docker compose --profile quality up -d sonar-db sonarqube
```

Open `http://localhost:9000`, sign in with `admin` / `admin`, change the password, then create a user token from SonarQube.

Generate backend coverage and compiled Java bytecode for analysis. Use the Docker command if Maven is not installed locally:

```powershell
mvn clean verify -Pcoverage
```

```powershell
docker compose --profile quality-coverage run --rm sonar-coverage
```
The coverage command writes each service's JaCoCo XML report under `target/site/jacoco/jacoco.xml`; run the scanner after this step so SonarQube can import the reports.

Run the scanner in Docker, replacing the token value:

```powershell
$env:SONAR_TOKEN = "your-sonarqube-token"
docker compose --profile quality --profile quality-scan run --rm sonar-scanner
```

Stop only the SonarQube containers when finished:

```powershell
docker compose stop sonarqube sonar-db
```

Avoid `docker compose down -v` unless you intentionally want to remove SonarQube data and the existing application volumes.

## Local Backend Development

Start infrastructure first, either through Docker Compose or local installs:

```powershell
docker compose up -d mysql redis rabbitmq elasticsearch eureka-server
```

Build all backend modules:

```powershell
mvn clean package
```

Run one service from its module directory:

```powershell
mvn spring-boot:run
```

Recommended startup order for all services outside Docker:

1. `eureka-server`
2. MySQL, Redis, RabbitMQ, Elasticsearch
3. `auth-service`
4. `post-service`
5. `comment-service`
6. `like-service`
7. `follow-service`
8. `notification-service`
9. `media-service`
10. `search-service`
11. `api-gateway`

## Frontend Development

The frontend lives in `connectsphere-frontend`.

```powershell
cd connectsphere-frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:3000` and calls the gateway at:

```text
http://localhost:8080/api/v1
```

See `connectsphere-frontend/README.md` for frontend-specific details.

## Useful Commands

Build backend:

```powershell
mvn clean package
```

Run backend tests:

```powershell
mvn test
```

Build one Docker service:

```powershell
docker compose build auth-service
```

Restart one Docker service after changes:

```powershell
docker compose up -d --build --no-deps auth-service
```

View logs:

```powershell
docker compose logs -f auth-service
```

Frontend tests:

```powershell
cd connectsphere-frontend
npm run test:run
```

Frontend production build:

```powershell
cd connectsphere-frontend
npm run build
```

## API Routing

The frontend should call the API gateway, not individual backend services.

| Gateway path | Routed service |
| --- | --- |
| `/api/v1/auth/**` | auth-service |
| `/api/v1/posts/**` | post-service |
| `/api/v1/comments/**` | comment-service |
| `/api/v1/likes/**` | like-service |
| `/api/v1/follows/**` | follow-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/reports/**` | notification-service |
| `/api/v1/media/**` | media-service |
| `/api/v1/stories/**` | media-service |
| `/api/v1/search/**` | search-service |

## Key Features

- User registration, login, JWT refresh, logout
- Profile editing and user lookup
- Public, followers-only, and private posts
- Feed generation from public posts and follow graph
- Threaded comments and replies
- Reactions for posts, comments, and stories
- Follow/unfollow, follower lists, following lists, mutuals, suggestions
- Notifications for social actions
- Media uploads and 24-hour stories
- Search for posts, users, hashtags, and trending tags
- Admin pages for users, posts, reports, and analytics
- Dockerized local development

## Troubleshooting

If services cannot talk to each other in Docker, confirm the environment URLs in `docker-compose.yml` use Compose service names such as `http://auth-service:8081`, not `localhost`.

If the frontend returns `401` after backend fixes, clear the old browser session or log out and log in again.

If MySQL connection fails, wait for the `cs-mysql` container to become healthy and then restart the affected service.

If a service does not show up in Eureka, check its logs and verify `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`.

If search endpoints fail, check both `search-service` and Elasticsearch health:

```powershell
docker compose ps search-service elasticsearch
```
