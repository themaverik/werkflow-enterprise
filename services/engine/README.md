# Flowable Engine Service

Central BPM workflow orchestration service for werkflow enterprise platform.

## Overview

This service provides the core Flowable BPMN engine that orchestrates all business processes across departments.

## Responsibilities

- Process definition management (deploy, version control)
- Process instance execution
- Task management and assignment
- Process variable management
- Event handling and messaging
- Workflow monitoring and history

## Technology Stack

- Java 17
- Spring Boot 3.3.x
- Flowable 7.0.x
- PostgreSQL 15 (schema: flowable)
- OAuth2/JWT authentication

## Port

- **8081** - HTTP REST API

## Status

**TODO**: To be implemented in Phase 1, Week 3-4

This service will be extracted from the current services/hr implementation, which currently contains embedded Flowable engine logic.

## API Endpoints (Planned)

- `POST /api/process-definitions/deploy` - Deploy BPMN process
- `GET /api/process-definitions` - List process definitions
- `POST /api/process-instances` - Start process instance
- `GET /api/process-instances/{id}` - Get process instance
- `GET /api/tasks` - List tasks
- `POST /api/tasks/{id}/complete` - Complete task
- `GET /api/history/process-instances` - Process history

## Configuration

See `config/env/.env.engine` for service-specific configuration.
