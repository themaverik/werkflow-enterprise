# Admin Service

User, organization, department, and role management service for werkflow enterprise platform.

## Overview

This service provides centralized identity and access management (IAM) functionality across all departments.

## Responsibilities

- User management (CRUD, profiles)
- Organization hierarchy management
- Department management
- Role and permission management
- Team management
- User-role assignment
- Cross-department user lookup

## Technology Stack

- Java 17
- Spring Boot 3.3.x
- PostgreSQL 15 (schema: admin_service)
- OAuth2/JWT authentication
- Integration with Keycloak for SSO

## Port

- **8083** - HTTP REST API

## Status

**TODO**: To be implemented in Phase 1, Week 7-8

## API Endpoints (Planned)

### Users
- `POST /api/users` - Create user
- `GET /api/users` - List users
- `GET /api/users/{id}` - Get user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Organizations
- `POST /api/organizations` - Create organization
- `GET /api/organizations` - List organizations
- `GET /api/organizations/{id}` - Get organization

### Departments
- `POST /api/departments` - Create department
- `GET /api/departments` - List departments
- `GET /api/departments/{id}` - Get department
- `PUT /api/departments/{id}` - Update department
- `DELETE /api/departments/{id}` - Delete department

### Roles
- `POST /api/roles` - Create role
- `GET /api/roles` - List roles
- `POST /api/users/{userId}/roles/{roleId}` - Assign role to user

## Configuration

See `config/env/.env.admin` for service-specific configuration.
