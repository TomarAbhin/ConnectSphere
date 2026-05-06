# ConnectSphere

ConnectSphere is a Spring Boot microservices social platform with a React frontend. It includes user authentication, posts, comments, reactions, follows, notifications, media uploads, search, and an API gateway.

## Architecture

The project is organized as a multi-module Maven workspace:

- auth-service: registration, login, profiles, admin user management
- post-service: create, edit, delete, feed, visibility, post counters
- comment-service: threaded comments, replies, comment likes, moderation
- like-service: reactions for posts and comments
- follow-service: follows, followers, suggestions, mutuals
- notification-service: in-app notifications and email alerts
- media-service: uploads, file serving, and stories
- search-service: post indexing, hashtag search, and trending tags
- api-gateway: single entry point for the backend
- eureka-server: service registry for discovery
- connectsphere-frontend: React client UI

## Requirements

- Java 21
- Maven 3.9+ if you want to run the backend outside Docker
- Node.js 18+ if you want to run the frontend outside Docker
- Docker Desktop if you want the containerized setup

## Quick Start With Docker

Docker is the easiest way to run the full stack.

1. Install Docker Desktop for Windows from the official Docker website.
2. Start Docker Desktop and make sure the engine is running.
3. From the repository root, run:

```powershell
docker compose up -d
```

4. Open these URLs:

- Frontend: http://localhost:3000
- API gateway: http://localhost:8080
- Eureka server: http://localhost:8761
- RabbitMQ management: http://localhost:15672

5. Stop the stack with:

```powershell
docker compose down
```

## Local Development Without Docker

If you prefer running services directly on your machine, start them in this order:

1. Eureka server on port 8761
2. MySQL, Redis, RabbitMQ, and Elasticsearch
3. auth-service
4. post-service
5. comment-service
6. like-service
7. follow-service
8. notification-service
9. media-service
10. search-service
11. api-gateway
12. Frontend on port 3000

Each backend service has its own Maven module and Dockerfile under its module directory.

### Backend

From the repository root, you can build all backend modules with:

```powershell
mvn clean package -DskipTests
```

To run a specific module during development, use the module directory and run its Spring Boot app with your IDE or Maven.

### Frontend

From the frontend directory:

```powershell
npm install
npm run dev
```

The frontend runs on http://localhost:3000.

## Testing

### Frontend tests

From the frontend directory:

```powershell
npm run test:run
```

### Backend tests

Run backend tests from the repository root when Maven is available:

```powershell
mvn test
```

## Service Ports

- 8761: Eureka server
- 8080: API gateway
- 8081: auth-service
- 8082: post-service
- 8083: comment-service
- 8084: like-service
- 8085: follow-service
- 8086: notification-service
- 8087: media-service
- 8088: search-service
- 3000: frontend

## Features

- User authentication and profile management
- Admin moderation for users and posts
- Social feed, post creation, and post editing
- Threaded comments and replies
- Post and comment reactions
- Follow graph and suggested users
- Notifications and email alerts
- Media uploads and story support
- Search, hashtag indexing, and trending tags
- Dockerized local development stack
- Eureka service discovery

## Notes

- The backend services are configured to register with Eureka.
- The Docker Compose file starts the registry and supporting infrastructure before the microservices.
- The frontend currently uses the API gateway for most requests.

## Troubleshooting

- If a backend service cannot connect to MySQL, check that Docker is running and the database container is healthy.
- If services do not appear in Eureka, confirm that port 8761 is reachable and the services are using the correct registry URL.
- If the frontend cannot reach the backend, make sure the API gateway is running on port 8080.

