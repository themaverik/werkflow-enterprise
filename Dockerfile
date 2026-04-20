# ================================================================
# WERKFLOW ENTERPRISE PLATFORM - MULTI-STAGE DOCKERFILE
# ================================================================
# This Dockerfile builds all services and frontends for the
# werkflow enterprise platform in a single, optimized build.
# ================================================================

# ================================================================
# STAGE 1: Backend Base - Build werkflow-delegates first
# ================================================================
FROM maven:3.9-eclipse-temurin-21 AS backend-base

WORKDIR /build

# Copy and build werkflow-common first (required by other services)
COPY shared/common/pom.xml shared/common/pom.xml
COPY shared/common/src shared/common/src

RUN cd shared/common && mvn clean install -DskipTests -B

# Copy and build werkflow-delegates (required by other services)
COPY shared/delegates/pom.xml shared/delegates/pom.xml
COPY shared/delegates/src shared/delegates/src

# Build and install werkflow-delegates to local maven repo
RUN cd shared/delegates && mvn clean install -DskipTests -B

# Now copy service poms
COPY services/engine/pom.xml services/engine/pom.xml
COPY services/admin/pom.xml services/admin/pom.xml

# Download dependencies for all services (werkflow-common and werkflow-delegates now available locally)
RUN cd services/engine && mvn dependency:go-offline -B
RUN cd services/admin && mvn dependency:go-offline -B

# ================================================================
# STAGE 2: Engine Service Build
# ================================================================
FROM backend-base AS engine-service-build

WORKDIR /build/services/engine

# Copy source code
COPY services/engine/src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# ================================================================
# STAGE 3: Admin Service Build
# ================================================================
FROM backend-base AS admin-service-build

WORKDIR /build/services/admin

# Copy source code
COPY services/admin/src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# ================================================================
# STAGE 5: Frontend Base - Node.js dependencies
# ================================================================
FROM node:20-alpine AS frontend-base

WORKDIR /build

# Install pnpm for better monorepo support
RUN npm install -g pnpm

# ================================================================
# STAGE 6: Portal Build (unified frontend)
# ================================================================
FROM frontend-base AS portal-build

WORKDIR /build/frontends/portal

# Copy package files
COPY frontends/portal/package.json frontends/portal/package-lock.json* ./

# Install ALL dependencies (including devDependencies for build)
RUN npm install && npm cache clean --force

# Copy configuration files first (tsconfig, next.config, etc.)
COPY frontends/portal/tsconfig.json* ./
COPY frontends/portal/next.config.mjs* ./
COPY frontends/portal/tailwind.config.ts* ./
COPY frontends/portal/tailwind.config.js* ./
COPY frontends/portal/postcss.config.js* ./
COPY frontends/portal/postcss.config.mjs* ./
COPY frontends/portal/components.json* ./

# Copy source code
COPY frontends/portal/ ./

# Build the application
RUN npm run build

# ================================================================
# STAGE 8: Engine Service Runtime
# ================================================================
FROM eclipse-temurin:21-jre AS engine-service

# Add non-root user
RUN groupadd -r werkflow && useradd -r -g werkflow werkflow

WORKDIR /app

# Copy JAR from build stage
COPY --from=engine-service-build /build/services/engine/target/*.jar app.jar

# Create directories
RUN mkdir -p /app/logs /app/process-definitions && \
    chown -R werkflow:werkflow /app

USER werkflow

EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-jar", \
    "app.jar"]

# ================================================================
# STAGE 9: Admin Service Runtime
# ================================================================
FROM eclipse-temurin:21-jre AS admin-service

# Add non-root user
RUN groupadd -r werkflow && useradd -r -g werkflow werkflow

WORKDIR /app

# Copy JAR from build stage
COPY --from=admin-service-build /build/services/admin/target/*.jar app.jar

# Create directories
RUN mkdir -p /app/logs && \
    chown -R werkflow:werkflow /app

USER werkflow

EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-jar", \
    "app.jar"]

# ================================================================
# STAGE 10: Portal Runtime (unified frontend)
# ================================================================
FROM node:20-alpine AS portal

# Add non-root user
RUN addgroup -S werkflow && adduser -S werkflow -G werkflow

WORKDIR /app

# Copy built application
COPY --from=portal-build --chown=werkflow:werkflow /build/frontends/portal/.next/standalone ./
COPY --from=portal-build --chown=werkflow:werkflow /build/frontends/portal/.next/static ./.next/static
COPY --from=portal-build --chown=werkflow:werkflow /build/frontends/portal/public ./public

USER werkflow

EXPOSE 4000

ENV PORT=4000
ENV HOSTNAME=0.0.0.0
ENV NODE_ENV=production

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:4000/ || exit 1

CMD ["node", "server.js"]
