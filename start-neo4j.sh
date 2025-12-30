#!/bin/bash

# Neo4j Integration Startup Script
# This script helps start and initialize the Neo4j-based Opik environment

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DOCKER_COMPOSE_DIR="$SCRIPT_DIR/opik/deployment/docker-compose"
SCHEMA_FILE="$SCRIPT_DIR/opik/apps/opik-backend/src/main/resources/neo4j/schema.cypher"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Opik Neo4j Integration Startup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Docker is running
echo -e "${YELLOW}Checking Docker status...${NC}"
if ! docker ps > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running or not accessible${NC}"
    echo -e "${YELLOW}Please start Docker Desktop and sign in, then run this script again${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"
echo ""

# Navigate to docker-compose directory
cd "$DOCKER_COMPOSE_DIR"

# Start Neo4j and infrastructure services
echo -e "${YELLOW}Starting Neo4j and infrastructure services...${NC}"
docker-compose up -d neo4j redis minio mc

echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 5

# Wait for Neo4j to be ready
echo -e "${YELLOW}Waiting for Neo4j to be ready (this may take 30-60 seconds)...${NC}"
MAX_WAIT=60
WAIT_COUNT=0
until docker-compose exec -T neo4j cypher-shell -u neo4j -p password "RETURN 1" > /dev/null 2>&1; do
    echo -n "."
    sleep 2
    WAIT_COUNT=$((WAIT_COUNT + 2))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo -e "\n${RED}Error: Neo4j did not become ready in time${NC}"
        echo -e "${YELLOW}Check logs with: docker-compose logs neo4j${NC}"
        exit 1
    fi
done
echo -e "\n${GREEN}✓ Neo4j is ready${NC}"
echo ""

# Initialize schema
echo -e "${YELLOW}Initializing Neo4j schema (constraints and indexes)...${NC}"
if [ -f "$SCHEMA_FILE" ]; then
    cat "$SCHEMA_FILE" | docker-compose exec -T neo4j cypher-shell -u neo4j -p password
    echo -e "${GREEN}✓ Schema initialized successfully${NC}"
else
    echo -e "${RED}Warning: Schema file not found at $SCHEMA_FILE${NC}"
fi
echo ""

# Display service status
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Services Started Successfully${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Neo4j Browser:  ${GREEN}http://localhost:7474${NC}"
echo -e "  Username: ${YELLOW}neo4j${NC}"
echo -e "  Password: ${YELLOW}password${NC}"
echo ""
echo -e "Redis:          ${GREEN}localhost:6379${NC}"
echo -e "MinIO Console:  ${GREEN}http://localhost:9090${NC}"
echo ""

# Check if backend needs to be built
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}   Next Steps${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "1. Verify Neo4j schema in browser: http://localhost:7474"
echo "   Run: CALL db.schema.visualization()"
echo ""
echo "2. Build the backend (requires Java 21 and Maven):"
echo "   cd opik/apps/opik-backend"
echo "   mvn clean install -DskipTests"
echo ""
echo "3. Run integration tests:"
echo "   mvn test -Dtest=Neo4jIntegrationTest"
echo ""
echo "4. Start the backend:"
echo "   cd ../deployment/docker-compose"
echo "   docker-compose --profile opik up -d backend"
echo ""
echo -e "${YELLOW}Note: The backend will not fully start until all DAO implementations are complete${NC}"
echo -e "${YELLOW}See NEO4J_ROADMAP.md for remaining work${NC}"
echo ""

# Display service logs
echo -e "${YELLOW}To view service logs:${NC}"
echo "  docker-compose logs -f neo4j"
echo "  docker-compose logs -f redis"
echo ""

echo -e "${GREEN}Startup complete!${NC}"

