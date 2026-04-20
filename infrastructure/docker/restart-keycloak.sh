#!/bin/bash

# Werkflow Keycloak Restart Script
# This script rebuilds and restarts Keycloak with proper hostname configuration

set -e

echo "======================================"
echo "Werkflow Keycloak Restart"
echo "======================================"
echo ""

# Stop and remove existing Keycloak container
echo "1. Stopping existing Keycloak container..."
docker compose stop keycloak 2>/dev/null || true
docker compose rm -f keycloak 2>/dev/null || true
echo "   Done."
echo ""

# Start Keycloak with the new configuration
echo "2. Starting Keycloak with production mode and hostname configuration..."
docker compose up -d keycloak
echo "   Done."
echo ""

# Wait for Keycloak to be ready
echo "3. Waiting for Keycloak to be ready..."
echo "   This may take 30-60 seconds on first start..."

MAX_ATTEMPTS=30
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if curl -sf http://localhost:8090/health/ready > /dev/null 2>&1; then
    echo "   Keycloak is ready!"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "   Attempt $ATTEMPT/$MAX_ATTEMPTS - waiting..."
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo "   WARNING: Keycloak did not become ready in time."
  echo "   Check logs with: docker logs werkflow-keycloak"
  exit 1
fi

echo ""
echo "======================================"
echo "Verification"
echo "======================================"
echo ""

# Show hostname configuration from logs
echo "Hostname configuration:"
docker logs werkflow-keycloak 2>&1 | grep "Hostname settings" | tail -1
echo ""

# Check OIDC discovery endpoint
echo "OIDC Issuer URL:"
curl -s http://localhost:8090/realms/werkflow/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"' || echo "Failed to fetch"
echo ""
echo ""

echo "======================================"
echo "Success!"
echo "======================================"
echo ""
echo "Keycloak is now running with proper hostname configuration."
echo ""
echo "Access points:"
echo "  Admin Console:    http://localhost:8090/admin"
echo "  Realm (werkflow): http://localhost:8090/realms/werkflow"
echo "  Admin User:       admin"
echo "  Admin Password:   admin123"
echo ""
echo "The browser should now be able to access Keycloak at localhost:8090"
echo ""
