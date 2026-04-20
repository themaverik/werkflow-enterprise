# Shared Common Utilities

Common utilities, models, and helpers shared across all werkflow services.

## Overview

This module contains reusable code that is shared across multiple services to avoid duplication.

## Contents (Planned)

### DTOs
- Common data transfer objects
- API response wrappers
- Error response models

### Utilities
- Date/time utilities
- String manipulation helpers
- Validation utilities
- Encryption/decryption helpers

### Constants
- Common constants
- Error codes
- HTTP status codes

### Exceptions
- Custom exception classes
- Exception handlers

### Security
- JWT utilities
- OAuth2 helpers
- Permission utilities

## Technology Stack

- Java 17
- Spring Boot 3.3.x
- Jackson for JSON
- Apache Commons

## Usage

This module will be packaged as a library and included as a dependency in other services:

```xml
<dependency>
    <groupId>com.werkflow</groupId>
    <artifactId>werkflow-common</artifactId>
    <version>${werkflow.version}</version>
</dependency>
```

## Status

**TODO**: To be implemented during Phase 1 as needed
