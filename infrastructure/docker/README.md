# Werkflow Docker Infrastructure

Docker configuration for the werkflow enterprise platform.

## Files

- **docker-compose.yml** - Full production-like environment (all services in Docker)
- **docker-compose.dev.yml** - Development environment (infrastructure only)
- **init-db.sql** - Database initialization script (creates schemas)
- **Dockerfile** - Multi-stage build (located in project root)

## Quick Start

### Development Mode (Recommended)

Run only infrastructure in Docker, services on host:

```bash
# Start infrastructure
cd infrastructure/docker
docker-compose -f docker-compose.dev.yml up -d

# Verify
docker-compose -f docker-compose.dev.yml ps

# Run HR service locally
cd ../../services/hr
mvn spring-boot:run

# Run admin-portal locally
cd ../../frontends/admin-portal
npm run dev
```

**Access:**
- PostgreSQL: localhost:5433
- Keycloak: http://localhost:8090
- pgAdmin: http://localhost:5050

### Full Docker Mode

Run everything in Docker:

```bash
# Start all services
cd infrastructure/docker
docker-compose up -d --build

# Verify
docker-compose ps

# View logs
docker-compose logs -f hr-service
docker-compose logs -f admin-portal
```

**Access:**
- HR Service: http://localhost:8082
- Admin Portal: http://localhost:4000
- PostgreSQL: localhost:5433
- Keycloak: http://localhost:8090
- pgAdmin: http://localhost:5050

## Services

### Infrastructure

| Service | Port | Container Name | Description |
|---------|------|----------------|-------------|
| PostgreSQL | 5433 | werkflow-postgres | Main database |
| Keycloak | 8090 | werkflow-keycloak | OAuth2/JWT auth |
| pgAdmin | 5050 | werkflow-pgadmin | DB management UI |

### Backend Services

| Service | Port | Container Name | Status | Phase |
|---------|------|----------------|--------|-------|
| Engine | 8081 | werkflow-engine | TODO | Phase 1 |
| HR | 8082 | werkflow-hr | ✅ Ready | Current |
| Admin | 8083 | werkflow-admin | TODO | Phase 1 |

### Frontend Services

| Service | Port | Container Name | Status | Phase |
|---------|------|----------------|--------|-------|
| Admin Portal | 4000 | werkflow-admin-portal | ✅ Ready | Current |
| HR Portal | 4001 | werkflow-hr-portal | TODO | Phase 2 |

## Database Schemas

Single PostgreSQL instance with schema separation:

- **flowable** - Flowable BPM engine tables
- **hr_service** - HR domain tables
- **admin_service** - User/org/dept management
- **finance_service** - Finance domain (future)
- **procurement_service** - Procurement domain (future)
- **inventory_service** - Inventory domain (future)
- **legal_service** - Legal domain (future)

## Environment Variables

### Required Files

Create these files from examples:

```bash
# Backend services
cp ../../config/env/.env.shared.example ../../config/env/.env.shared
cp ../../config/env/.env.hr.example ../../config/env/.env.hr
cp ../../config/env/.env.engine.example ../../config/env/.env.engine
cp ../../config/env/.env.admin.example ../../config/env/.env.admin

# Frontend services
cp ../../frontends/admin-portal/.env.local.example ../../frontends/admin-portal/.env.local
```

### Key Variables

**Shared (config/env/.env.shared):**
- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `KEYCLOAK_URL`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`
- `JWT_SECRET`, `ENCRYPTION_KEY`
- `SMTP_*` for email

**Service-specific (config/env/.env.{service}):**
- `SERVER_PORT`
- `SPRING_DATASOURCE_SCHEMA`
- Service-specific configuration

## Common Commands

### Development Mode

```bash
# Start infrastructure
docker-compose -f docker-compose.dev.yml up -d

# Stop infrastructure
docker-compose -f docker-compose.dev.yml down

# View logs
docker-compose -f docker-compose.dev.yml logs -f

# Reset database
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
```

### Full Docker Mode

```bash
# Build and start all services
docker-compose up -d --build

# Stop all services
docker-compose down

# Rebuild specific service
docker-compose build hr-service
docker-compose up -d hr-service

# View logs for specific service
docker-compose logs -f hr-service

# Access service shell
docker-compose exec hr-service sh

# Reset everything (including volumes)
docker-compose down -v
docker-compose up -d --build
```

### Database Access

```bash
# Connect via psql
docker-compose exec postgres psql -U werkflow_admin -d werkflow

# Connect to specific schema
docker-compose exec postgres psql -U werkflow_admin -d werkflow -c "SET search_path TO hr_service;"

# View schemas
docker-compose exec postgres psql -U werkflow_admin -d werkflow -c "\dn"
```

## Health Checks

All services include health checks:

```bash
# Check service health
docker-compose ps

# View health check logs
docker inspect --format='{{json .State.Health}}' werkflow-postgres
docker inspect --format='{{json .State.Health}}' werkflow-hr
```

## Volumes

### Development Mode
- `postgres_data_dev` - PostgreSQL data
- `keycloak_postgres_data_dev` - Keycloak database
- `pgadmin_data_dev` - pgAdmin settings

### Full Docker Mode
- `postgres_data` - PostgreSQL data
- `keycloak_postgres_data` - Keycloak database
- `pgadmin_data` - pgAdmin settings
- `hr_documents` - HR document uploads
- `hr_logs` - HR service logs

## Network

All services communicate via `werkflow-network` bridge network.

**Internal DNS:**
- Services resolve by container name
- Example: HR service connects to `postgres:5432` internally

**External Access:**
- Services exposed via port mapping
- Example: Access postgres at `localhost:5433` from host

## Troubleshooting

### PostgreSQL Connection Issues

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Check PostgreSQL logs
docker-compose logs postgres

# Test connection
docker-compose exec postgres psql -U werkflow_admin -d werkflow -c "SELECT 1;"
```

### Service Won't Start

```bash
# Check build logs
docker-compose build hr-service

# Check runtime logs
docker-compose logs hr-service

# Check environment variables
docker-compose config
```

### Port Already in Use

```bash
# Find process using port
lsof -i :5433

# Change port in docker-compose.yml
ports:
  - "5434:5432"  # Use 5434 instead
```

### Clean Slate

```bash
# Stop everything
docker-compose down

# Remove all volumes (WARNING: deletes all data)
docker-compose down -v

# Remove all werkflow images
docker images | grep werkflow | awk '{print $3}' | xargs docker rmi -f

# Start fresh
docker-compose up -d --build
```

## Production Deployment

See ROADMAP-DRAFT.md Phase 3 for production deployment with:
- Kubernetes orchestration
- Helm charts
- CI/CD pipelines
- Terraform infrastructure

Current Docker Compose is for development and testing only.
