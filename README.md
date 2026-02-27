# RdX LogHealer

AI-powered log analysis and exception healing platform.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      LogHealer                               │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  Dashboard  │    │   Backend   │    │    Caddy    │     │
│  │  (Angular)  │◄──►│(Spring Boot)│◄──►│   (Proxy)   │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│         │                  │                                 │
│         │                  ▼                                 │
│         │         ┌───────────────┐                         │
│         │         │ PostgreSQL    │ ← Metadata              │
│         │         ├───────────────┤                         │
│         └────────►│ Elasticsearch │ ← Logs & Search         │
│                   ├───────────────┤                         │
│                   │    Redis      │ ← Cache & Queue         │
│                   └───────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start (Development)

### Backend
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Dashboard
```bash
cd dashboard
npm install
npm start
```

### Infrastructure (Docker)
```bash
docker compose up -d  # PostgreSQL, Elasticsearch, Redis
```

## Deployment

See [deploy/DEPLOYMENT.md](deploy/DEPLOYMENT.md) for DigitalOcean deployment instructions.

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/logs` | Ingest single log |
| `POST /api/v1/logs/batch` | Ingest batch logs |
| `GET /api/v1/logs/search` | Search logs |
| `GET /api/v1/dashboard/stats` | Dashboard statistics |
| `GET /api/v1/exceptions` | List exception groups |
| `GET /api/v1/health` | Health check |

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.x, Spring Data Elasticsearch
- **Frontend**: Angular 19, Chart.js
- **Database**: PostgreSQL 16, Elasticsearch 8.x, Redis 7
- **Deploy**: Docker, Caddy, GitHub Actions
