# Neo4j Integration - Implementation Roadmap

This document outlines the remaining work needed to complete the Neo4j integration for Opik.

## Current Status (as of completion)

### ✅ Completed Core Infrastructure
1. Dependencies configured (Neo4j Java Driver 5.27.0)
2. Configuration migrated (config.yml with Neo4j settings)
3. Docker Compose updated (Neo4j service defined)
4. DatabaseGraphModule created (Guice integration)
5. Neo4jTransactionTemplate implemented (reactive queries)
6. Schema defined (comprehensive constraints and indexes)
7. Health check implemented
8. Integration and performance tests created
9. Documentation completed (NEO4J_INTEGRATION.md)

### ✅ Completed DAO Implementations
1. **TraceDAONeo4jImpl** - Core trace operations with Cypher
2. **SpanDAONeo4jImpl** - Span operations with parent-child relationships
3. **ProjectDAONeo4jImpl** - Basic project CRUD operations

### ⚠️ Remaining Work

The following components still need Neo4j implementations to make the system fully functional:

## Priority 1: Critical DAOs (Required for Basic Functionality)

These DAOs are essential for the application to start and handle basic operations:

### State Management DAOs
1. **WorkspaceDAO** - Workspace CRUD operations
   - File: `com.comet.opik.domain.workspaces.WorkspaceDAO`
   - Operations: create, read, update, delete workspaces
   - Relationships: User → Workspace, Workspace → Project

2. **UserDAO** (if applicable) - User management
   - Operations: user authentication, profile management
   - Relationships: User → Workspace, User → ApiKey

3. **ApiKeyDAO** (if applicable) - API key management
   - Operations: create, validate, revoke API keys
   - Relationships: User → ApiKey, Workspace → ApiKey

### Analytics DAOs
4. **FeedbackScoreDAO** - Feedback and evaluation scores
   - File: `com.comet.opik.domain.FeedbackScoreDAO`
   - Operations: create, read feedback scores
   - Relationships: Trace → FeedbackScore, Span → FeedbackScore

5. **CommentDAO** - User comments on traces/spans
   - File: `com.comet.opik.domain.CommentDAO`
   - Operations: create, read, update, delete comments
   - Relationships: Trace → Comment, Span → Comment

6. **TraceThreadDAO** - Thread management for traces
   - File: `com.comet.opik.domain.threads.TraceThreadDAO`
   - Operations: thread creation, retrieval
   - Relationships: Trace → TraceThread

## Priority 2: Feature DAOs (Required for Advanced Features)

These DAOs support additional Opik features:

### Prompt Management
7. **PromptDAO** - Prompt template management
   - Operations: create, read, update, delete prompts
   - Relationships: Workspace → Prompt

8. **PromptVersionDAO** - Prompt versioning
   - Operations: version management
   - Relationships: Prompt → PromptVersion

### Dataset Management
9. **DatasetDAO** - Dataset operations
   - Operations: create, read, update, delete datasets
   - Relationships: Workspace → Dataset

10. **DatasetVersionDAO** - Dataset versioning
    - Relationships: Dataset → DatasetVersion

11. **DatasetItemDAO** - Dataset items
    - Relationships: DatasetVersion → DatasetItem

### Experiment Management
12. **ExperimentDAO** - Experiment tracking
    - Operations: create, read experiments
    - Relationships: Project → Experiment

13. **ExperimentItemDAO** - Experiment results
    - Relationships: Experiment → ExperimentItem

### Evaluation & Automation
14. **FeedbackDefinitionDAO** - Feedback score definitions
    - Operations: manage feedback score types
    - Relationships: Workspace → FeedbackDefinition

15. **AutomationRuleDAO** - Automation rule management
    - Operations: create, read automation rules
    - Relationships: Project → AutomationRule

16. **AutomationRuleEvaluatorDAO** - Rule evaluation
    - Operations: evaluate and log automation rules

## Priority 3: Supporting DAOs (Optional but Recommended)

### Observability & Monitoring
17. **AttachmentDAO** - File attachments
    - Relationships: Trace → Attachment, Span → Attachment

18. **GuardrailsDAO** - Guardrail validation
    - Relationships: Trace → Guardrail, Span → Guardrail

19. **OptimizationDAO** - Optimization tracking
    - Relationships: Project → Optimization

### Configuration & Settings
20. **DashboardDAO** - Dashboard management
    - Relationships: Workspace → Dashboard

21. **AlertDAO** - Alert configuration
    - Relationships: Workspace → Alert

22. **WebhookDAO** - Webhook management
    - Relationships: Workspace → Webhook

23. **LlmProviderApiKeyDAO** - LLM provider credentials
    - Operations: manage API keys for LLM providers

## Implementation Pattern

For each DAO, follow this pattern (see TraceDAONeo4jImpl as reference):

```java
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EntityDAONeo4jImpl {
    
    private final Neo4jTransactionTemplate neo4jTemplate;

    public Mono<Entity> save(Entity entity) {
        String cypher = """
                MATCH (parent:Parent {id: $parentId})
                MERGE (e:Entity {id: $id})
                ON CREATE SET e.prop = $value, e.createdAt = datetime($createdAt)
                ON MATCH SET e.prop = $value, e.updatedAt = datetime($updatedAt)
                MERGE (parent)-[:RELATIONSHIP]->(e)
                RETURN e
                """;
        
        Map<String, Object> params = buildParams(entity);
        return neo4jTemplate.executeWrite(cypher, params, this::mapToEntity);
    }

    public Mono<Entity> findById(UUID id) {
        String cypher = "MATCH (e:Entity {id: $id}) RETURN e";
        return neo4jTemplate.executeRead(cypher, Map.of("id", id.toString()), this::mapToEntity);
    }

    private Entity mapToEntity(org.neo4j.driver.Record record) {
        var node = record.get("e").asNode();
        // Map node properties to Entity
        return Entity.builder()
                .id(UUID.fromString(node.get("id").asString()))
                .build();
    }
}
```

## Service Layer Updates

After implementing DAOs, update corresponding services to remove old database dependencies:

1. **TraceService** - Already using TraceDAO interface, should work
2. **SpanService** - Already using SpanDAO interface, should work
3. **ProjectService** - Update to use ProjectDAONeo4jImpl
4. **WorkspaceService** - Update for Neo4j
5. Other services as needed

## Testing Strategy

For each DAO implementation:

1. **Unit Tests** - Test individual methods
2. **Integration Tests** - Test with Testcontainers Neo4j
3. **Performance Tests** - Verify query performance

Example test structure:
```java
@Testcontainers
public class EntityDAONeo4jImplTest {
    
    @Container
    private static final Neo4jContainer<?> neo4jContainer = 
        new Neo4jContainer<>("neo4j:5.27.0-community");
    
    @Test
    void shouldCreateEntity() {
        // Test implementation
    }
}
```

## Migration Strategy

1. **Phase 1: Core DAOs** (Priority 1)
   - Implement critical DAOs
   - Update service layer
   - Run integration tests

2. **Phase 2: Feature DAOs** (Priority 2)
   - Implement feature DAOs
   - Enable advanced features
   - Test end-to-end workflows

3. **Phase 3: Optional DAOs** (Priority 3)
   - Complete remaining DAOs
   - Full feature parity
   - Performance optimization

## Quick Start for Contributors

To implement a new DAO:

1. Find the existing DAO interface (e.g., `WorkspaceDAO.java`)
2. Create Neo4j implementation (e.g., `WorkspaceDAONeo4jImpl.java`)
3. Use Neo4jTransactionTemplate for queries
4. Write Cypher queries following the schema in `schema.cypher`
5. Add tests in `src/test/java/`
6. Update the DAO interface `@ImplementedBy` annotation

## Running the Project

### Prerequisites
- Docker Desktop running and signed in
- Neo4j service started

### Start Infrastructure
```bash
cd deployment/docker-compose
docker-compose up -d neo4j redis minio mc
```

### Initialize Schema
```bash
cat ../../apps/opik-backend/src/main/resources/neo4j/schema.cypher | \
docker-compose exec -T neo4j cypher-shell -u neo4j -p password
```

### Build Backend (when Java/Maven available)
```bash
cd apps/opik-backend
mvn clean install -DskipTests  # Skip tests for faster build
mvn test  # Run tests after build
```

### Run Tests
```bash
# Integration tests
mvn test -Dtest=Neo4jIntegrationTest

# Performance tests
mvn test -Dtest=Neo4jPerformanceTest
```

## Expected Timeline

- **Core DAOs (Priority 1)**: 2-3 days
- **Feature DAOs (Priority 2)**: 3-5 days
- **Optional DAOs (Priority 3)**: 2-3 days
- **Total**: 7-11 days for complete implementation

## Success Criteria

✅ All DAOs have Neo4j implementations
✅ All services work with Neo4j
✅ Integration tests pass
✅ Performance benchmarks meet requirements
✅ Application starts successfully
✅ End-to-end workflows function correctly
✅ Documentation is complete and accurate

## Current Blockers

1. **Docker Sign-in Required** - Docker Desktop needs authentication
2. **Java/Maven Not Available Locally** - Need to use Docker for builds or install locally
3. **Remaining DAO Implementations** - ~20 DAOs still need Neo4j versions

## Next Immediate Steps

1. **Sign into Docker Desktop** to enable container operations
2. **Start Neo4j service** with docker-compose
3. **Initialize schema** using the provided Cypher script
4. **Implement Priority 1 DAOs** (Workspace, FeedbackScore, Comment, etc.)
5. **Test basic trace/span operations** with Neo4j
6. **Iterate on remaining DAOs** following the pattern

## Resources

- Implementation examples: `TraceDAONeo4jImpl.java`, `SpanDAONeo4jImpl.java`
- Schema reference: `apps/opik-backend/src/main/resources/neo4j/schema.cypher`
- Integration guide: `NEO4J_INTEGRATION.md`
- Test examples: `Neo4jIntegrationTest.java`, `Neo4jPerformanceTest.java`

---

**Status**: Foundation complete, ~20 DAOs remaining for full functionality
**Last Updated**: Implementation session completion
**Next Milestone**: Priority 1 DAOs completed

