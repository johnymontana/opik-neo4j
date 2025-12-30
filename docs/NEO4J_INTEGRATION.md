# Neo4j Integration for Opik

This document describes the Neo4j integration for Opik, which replaces both MySQL and ClickHouse with a unified graph database solution.

## Overview

The Neo4j integration consolidates Opik's data storage into a single graph database, providing:

- **Unified Data Model**: All state and analytics data in one connected graph
- **Natural Relationships**: Express workspace → project → trace → span as native graph relationships
- **Flexible Schema**: Add new node types and relationships without complex migrations
- **Powerful Queries**: Use Cypher's pattern matching for complex analytical queries
- **Graph Algorithms**: Apply graph analytics to understand execution patterns

## Architecture Changes

### Previous Architecture (MySQL + ClickHouse)
- **MySQL**: State database for workspaces, projects, users, API keys
- **ClickHouse**: Analytics database for traces, spans, feedback

### New Architecture (Neo4j Only)
- **Neo4j**: Unified graph database storing all entities and their relationships

## Graph Data Model

### Node Types

**State Layer:**
- `User` - Opik user accounts
- `ApiKey` - API keys for authentication
- `Workspace` - Opik workspaces (tenants)
- `Project` - Projects within workspaces
- `FeedbackDefinition` - Feedback score definitions
- `Prompt` - Prompt templates
- `Dataset` - Datasets for evaluation
- `Experiment` - Experiment runs

**Analytics Layer:**
- `Trace` - Top-level execution traces
- `Span` - Individual operations within traces
- `TraceThread` - Conversation threads
- `FeedbackScore` - Evaluation scores
- `Comment` - User comments
- `Attachment` - File attachments

### Relationship Types

**State Relationships:**
- `(:Workspace)-[:CONTAINS]->(:Project)`
- `(:User)-[:HAS_WORKSPACE]->(:Workspace)`
- `(:User)-[:HAS_API_KEY]->(:ApiKey)`
- `(:Workspace)-[:HAS_DATASET]->(:Dataset)`
- `(:Workspace)-[:HAS_PROMPT]->(:Prompt)`
- `(:Project)-[:HAS_EXPERIMENT]->(:Experiment)`

**Analytics Relationships:**
- `(:Project)-[:HAS_TRACE]->(:Trace)`
- `(:Trace)-[:HAS_SPAN]->(:Span)`
- `(:Span)-[:PARENT_OF]->(:Span)` - Span hierarchy
- `(:Trace)-[:IN_THREAD]->(:TraceThread)`
- `(:Trace)-[:HAS_FEEDBACK]->(:FeedbackScore)`
- `(:Span)-[:HAS_FEEDBACK]->(:FeedbackScore)`

## Configuration

### Environment Variables

Replace MySQL and ClickHouse configuration with Neo4j settings:

```yaml
# Neo4j Configuration (in config.yml)
neo4jDatabase:
  uri: ${NEO4J_URI:-bolt://localhost:7687}
  username: ${NEO4J_USERNAME:-neo4j}
  password: ${NEO4J_PASSWORD:-password}
  database: ${NEO4J_DATABASE:-neo4j}
  maxConnectionPoolSize: ${NEO4J_MAX_POOL_SIZE:-50}
  connectionAcquisitionTimeout: ${NEO4J_CONN_TIMEOUT:-60s}
  maxConnectionLifetime: ${NEO4J_MAX_CONN_LIFETIME:-3600s}
  encrypted: ${NEO4J_ENCRYPTED:-false}
  trustStrategy: ${NEO4J_TRUST_STRATEGY:-TRUST_ALL_CERTIFICATES}
```

### Docker Compose

The Neo4j service is configured in `docker-compose.yaml`:

```yaml
services:
  neo4j:
    image: neo4j:5.27.0-community
    hostname: neo4j
    environment:
      NEO4J_AUTH: neo4j/password
      NEO4J_server_memory_heap_max__size: 2G
      NEO4J_server_memory_pagecache_size: 1G
      NEO4J_PLUGINS: '["apoc"]'
    ports:
      - "7474:7474"  # HTTP
      - "7687:7687"  # Bolt
    volumes:
      - neo4j-data:/data
      - neo4j-logs:/logs
    healthcheck:
      test: ["CMD", "cypher-shell", "-u", "neo4j", "-p", "password", "MATCH (n) RETURN count(n) LIMIT 1"]
      interval: 5s
      timeout: 10s
      retries: 60
```

## Installation and Setup

### 1. Start Neo4j with Docker Compose

```bash
cd deployment/docker-compose
docker-compose up -d neo4j
```

### 2. Initialize Schema

Run the schema initialization script to create constraints and indexes:

```bash
cat apps/opik-backend/src/main/resources/neo4j/schema.cypher | \
docker-compose exec -T neo4j cypher-shell -u neo4j -p password
```

### 3. Start Opik Backend

```bash
docker-compose up -d backend
```

## Schema Management

### Constraints

Uniqueness constraints ensure data integrity:

```cypher
CREATE CONSTRAINT trace_id_unique IF NOT EXISTS 
FOR (t:Trace) REQUIRE t.id IS UNIQUE;

CREATE CONSTRAINT span_id_unique IF NOT EXISTS 
FOR (s:Span) REQUIRE s.id IS UNIQUE;

CREATE CONSTRAINT project_id_unique IF NOT EXISTS 
FOR (p:Project) REQUIRE p.id IS UNIQUE;
```

### Indexes

Performance indexes for common queries:

```cypher
// Trace indexes
CREATE INDEX trace_start_time IF NOT EXISTS 
FOR (t:Trace) ON (t.startTime);

CREATE INDEX trace_project_id IF NOT EXISTS 
FOR (t:Trace) ON (t.projectId);

// Span indexes
CREATE INDEX span_trace_id IF NOT EXISTS 
FOR (s:Span) ON (s.traceId);

CREATE INDEX span_start_time IF NOT EXISTS 
FOR (s:Span) ON (s.startTime);
```

## Code Architecture

### Neo4j Module

**DatabaseGraphModule** - Initializes Neo4j driver and provides dependency injection:

```java
@Provides
@Singleton
public Driver getDriver() {
    return driver;
}
```

### Transaction Template

**Neo4jTransactionTemplate** - Provides reactive transaction handling using Project Reactor:

```java
public <T> Mono<T> executeWrite(String query, Map<String, Object> parameters, 
                                 Function<Record, T> mapper) {
    // Executes Cypher query in write transaction
}

public <T> Flux<T> executeReadFlux(String query, Map<String, Object> parameters, 
                                    Function<Record, T> mapper) {
    // Executes Cypher query in read transaction, returns multiple results
}
```

### DAO Layer

**TraceDAONeo4jImpl** - Neo4j implementation of TraceDAO using Cypher:

```java
@Override
public Mono<UUID> insert(Trace trace, Connection connection) {
    String cypher = """
        MATCH (p:Project {id: $projectId})
        CREATE (t:Trace {...properties...})
        CREATE (p)-[:HAS_TRACE]->(t)
        RETURN t.id as id
        """;
    return neo4jTemplate.executeWrite(cypher, params, record -> 
        UUID.fromString(record.get("id").asString()));
}
```

**SpanDAONeo4jImpl** - Neo4j implementation of SpanDAO:

```java
@Override
public Mono<Long> batchInsert(List<Span> spans) {
    String cypher = """
        UNWIND $spans AS spanData
        MATCH (t:Trace {id: spanData.traceId})
        CREATE (s:Span {...properties...})
        CREATE (t)-[:HAS_SPAN]->(s)
        """;
    return neo4jTemplate.executeWriteVoid(cypher, params)
            .thenReturn((long) spans.size());
}
```

## Example Cypher Queries

### Find All Traces for a Project

```cypher
MATCH (p:Project {id: $projectId})-[:HAS_TRACE]->(t:Trace)
RETURN t
ORDER BY t.startTime DESC
SKIP $skip LIMIT $limit
```

### Get Trace with All Spans

```cypher
MATCH (t:Trace {id: $traceId})-[:HAS_SPAN]->(s:Span)
OPTIONAL MATCH (s)-[:PARENT_OF*]->(child:Span)
RETURN t, collect(s), collect(child)
```

### Find Span Hierarchy

```cypher
MATCH path = (parent:Span {id: $spanId})-[:PARENT_OF*]->(child:Span)
RETURN path
ORDER BY length(path)
```

### Get Traces with Errors in Last Hour

```cypher
MATCH (t:Trace)
WHERE t.errorInfo IS NOT NULL
  AND t.startTime >= datetime() - duration('PT1H')
RETURN t
ORDER BY t.startTime DESC
```

## Performance Considerations

### Batch Operations

Use `UNWIND` for efficient batch inserts:

```cypher
UNWIND $traces AS traceData
CREATE (t:Trace {
    id: traceData.id,
    name: traceData.name,
    // ... other properties
})
```

### Indexing Strategy

1. **Unique constraints** on ID fields (automatically indexed)
2. **Indexes** on frequently filtered properties (projectId, workspaceId, timestamps)
3. **Composite indexes** for common query patterns (projectId + startTime)
4. **Fulltext indexes** for text search on names and content

### Memory Configuration

For production deployments, configure Neo4j memory settings:

```yaml
environment:
  NEO4J_server_memory_heap_max__size: 4G
  NEO4J_server_memory_pagecache_size: 2G
```

## Testing

### Integration Tests

Integration tests use Testcontainers for Neo4j:

```java
@Testcontainers
public class Neo4jIntegrationTest {
    @Container
    private static final Neo4jContainer<?> neo4jContainer = 
        new Neo4jContainer<>("neo4j:5.27.0-community")
            .withAdminPassword("testpassword");
}
```

### Performance Tests

Performance benchmarks measure:
- Batch insert throughput
- Indexed query performance
- Relationship traversal speed
- Update performance

Run tests with:

```bash
mvn test -Dtest=Neo4jPerformanceTest
```

## Migration from MySQL/ClickHouse

### Data Migration Strategy

1. **Export existing data** from MySQL and ClickHouse
2. **Transform to graph model** - map tables to nodes and foreign keys to relationships
3. **Bulk import** using Neo4j's `UNWIND` or `neo4j-admin import` tool
4. **Verify data integrity** - check counts and relationships
5. **Switch configuration** - update backend to use Neo4j

### Migration Tools

Create migration scripts:

```java
// Example: Migrate traces from ClickHouse to Neo4j
public Mono<Void> migrateTraces() {
    return clickhouseDAO.findAllTraces()
        .buffer(1000)  // Batch size
        .flatMap(traces -> neo4jDAO.batchInsert(traces))
        .then();
}
```

## Monitoring and Maintenance

### Health Check

The Neo4j health check verifies connectivity:

```java
@Override
protected Result check() throws Exception {
    try (Session session = driver.session()) {
        var result = session.run("RETURN 1 AS result");
        if (result.hasNext() && result.next().get("result").asInt() == 1) {
            return Result.healthy("Neo4j is up and running");
        }
    } catch (Exception e) {
        return Result.unhealthy("Neo4j is not accessible: " + e.getMessage());
    }
}
```

### Neo4j Browser

Access Neo4j Browser at `http://localhost:7474` to:
- Visualize graph structure
- Run Cypher queries interactively
- Monitor query performance
- View database statistics

### Useful Monitoring Queries

```cypher
// Count nodes by type
MATCH (n)
RETURN labels(n) AS nodeType, count(n) AS count
ORDER BY count DESC;

// Count relationships by type
MATCH ()-[r]->()
RETURN type(r) AS relType, count(r) AS count
ORDER BY count DESC;

// Database statistics
CALL db.stats.retrieve('GRAPH COUNTS');
```

## Troubleshooting

### Common Issues

1. **Connection timeout**
   - Check Neo4j is running: `docker-compose ps neo4j`
   - Verify network connectivity
   - Check firewall rules for port 7687

2. **Out of memory**
   - Increase heap size: `NEO4J_server_memory_heap_max__size`
   - Increase page cache: `NEO4J_server_memory_pagecache_size`
   - Optimize queries to reduce memory usage

3. **Slow queries**
   - Add appropriate indexes
   - Use `EXPLAIN` and `PROFILE` to analyze queries
   - Optimize Cypher patterns (avoid Cartesian products)

### Logs

View Neo4j logs:

```bash
docker-compose logs neo4j
```

## Benefits of Neo4j Integration

1. **Simplified Architecture**: Single database instead of MySQL + ClickHouse
2. **Natural Data Model**: Graph structure matches domain model
3. **Flexible Schema**: Easy to add new relationships and node types
4. **Powerful Queries**: Cypher pattern matching for complex analytics
5. **Graph Algorithms**: Apply graph analytics to trace data
6. **Operational Simplicity**: One database to backup, monitor, and scale

## Next Steps

1. Implement remaining DAO methods for full compatibility
2. Add graph algorithms for trace analysis
3. Optimize Cypher queries based on production workload
4. Implement data migration tools for existing installations
5. Add monitoring dashboards for Neo4j metrics
6. Consider Neo4j Enterprise for clustering and advanced features

## Resources

- [Neo4j Java Driver Documentation](https://neo4j.com/docs/java-manual/current/)
- [Cypher Query Language](https://neo4j.com/docs/cypher-manual/current/)
- [Neo4j Performance Tuning](https://neo4j.com/docs/operations-manual/current/performance/)
- [Project Reactor Documentation](https://projectreactor.io/docs/core/release/reference/)

