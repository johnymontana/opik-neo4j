package com.comet.opik.infrastructure.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Neo4j using Testcontainers.
 * These tests verify that the Neo4j database integration works correctly.
 */
@Testcontainers
public class Neo4jIntegrationTest {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.27.0-community")
            .withAdminPassword("testpassword")
            .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
            .withEnv("NEO4J_AUTH", "neo4j/testpassword");

    private static Driver driver;

    @BeforeAll
    static void setUp() {
        neo4jContainer.start();
        
        driver = GraphDatabase.driver(
                neo4jContainer.getBoltUrl(),
                AuthTokens.basic("neo4j", "testpassword")
        );
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    void testNeo4jConnection() {
        // Test basic connectivity
        try (Session session = driver.session()) {
            var result = session.run("RETURN 1 AS result");
            assertTrue(result.hasNext());
            assertEquals(1, result.next().get("result").asInt());
        }
    }

    @Test
    void testCreateTraceNode() {
        // Test creating a Trace node
        try (Session session = driver.session()) {
            var result = session.run("""
                    CREATE (t:Trace {
                        id: 'test-trace-id',
                        name: 'Test Trace',
                        projectId: 'test-project-id',
                        workspaceId: 'test-workspace-id'
                    })
                    RETURN t.id AS id
                    """);
            
            assertTrue(result.hasNext());
            assertEquals("test-trace-id", result.next().get("id").asString());
            
            // Clean up
            session.run("MATCH (t:Trace {id: 'test-trace-id'}) DETACH DELETE t");
        }
    }

    @Test
    void testCreateSpanWithRelationship() {
        // Test creating a Span node with relationship to Trace
        try (Session session = driver.session()) {
            // Create trace
            session.run("""
                    CREATE (t:Trace {
                        id: 'test-trace-id-2',
                        name: 'Test Trace 2',
                        projectId: 'test-project-id',
                        workspaceId: 'test-workspace-id'
                    })
                    """);
            
            // Create span with relationship
            var result = session.run("""
                    MATCH (t:Trace {id: 'test-trace-id-2'})
                    CREATE (s:Span {
                        id: 'test-span-id',
                        name: 'Test Span',
                        traceId: 'test-trace-id-2',
                        projectId: 'test-project-id',
                        workspaceId: 'test-workspace-id'
                    })
                    CREATE (t)-[:HAS_SPAN]->(s)
                    RETURN s.id AS id
                    """);
            
            assertTrue(result.hasNext());
            assertEquals("test-span-id", result.next().get("id").asString());
            
            // Verify relationship
            var relResult = session.run("""
                    MATCH (t:Trace {id: 'test-trace-id-2'})-[:HAS_SPAN]->(s:Span)
                    RETURN count(s) AS spanCount
                    """);
            
            assertTrue(relResult.hasNext());
            assertEquals(1, relResult.next().get("spanCount").asInt());
            
            // Clean up
            session.run("MATCH (t:Trace {id: 'test-trace-id-2'}) DETACH DELETE t");
            session.run("MATCH (s:Span {id: 'test-span-id'}) DETACH DELETE s");
        }
    }

    @Test
    void testCreateConstraints() {
        // Test creating uniqueness constraints
        try (Session session = driver.session()) {
            session.run("CREATE CONSTRAINT test_trace_id IF NOT EXISTS FOR (t:Trace) REQUIRE t.id IS UNIQUE");
            session.run("CREATE CONSTRAINT test_span_id IF NOT EXISTS FOR (s:Span) REQUIRE s.id IS UNIQUE");
            
            // Verify constraints exist
            var result = session.run("SHOW CONSTRAINTS");
            assertTrue(result.hasNext());
        }
    }

    @Test
    void testCreateIndexes() {
        // Test creating indexes
        try (Session session = driver.session()) {
            session.run("CREATE INDEX test_trace_start_time IF NOT EXISTS FOR (t:Trace) ON (t.startTime)");
            session.run("CREATE INDEX test_span_start_time IF NOT EXISTS FOR (s:Span) ON (s.startTime)");
            
            // Verify indexes exist
            var result = session.run("SHOW INDEXES");
            assertTrue(result.hasNext());
        }
    }

    @Test
    void testBatchInsertWithUnwind() {
        // Test batch insert using UNWIND
        try (Session session = driver.session()) {
            session.run("""
                    UNWIND [
                        {id: 'trace-1', name: 'Trace 1'},
                        {id: 'trace-2', name: 'Trace 2'},
                        {id: 'trace-3', name: 'Trace 3'}
                    ] AS traceData
                    CREATE (t:Trace {
                        id: traceData.id,
                        name: traceData.name,
                        projectId: 'test-project',
                        workspaceId: 'test-workspace'
                    })
                    """);
            
            // Verify all traces were created
            var result = session.run("""
                    MATCH (t:Trace)
                    WHERE t.projectId = 'test-project'
                    RETURN count(t) AS traceCount
                    """);
            
            assertTrue(result.hasNext());
            assertEquals(3, result.next().get("traceCount").asInt());
            
            // Clean up
            session.run("MATCH (t:Trace) WHERE t.projectId = 'test-project' DETACH DELETE t");
        }
    }

    @Test
    void testQueryWithFiltering() {
        // Test filtering queries
        try (Session session = driver.session()) {
            // Create test data
            session.run("""
                    CREATE (t1:Trace {id: 'trace-f1', name: 'Filter Test 1', projectId: 'project-f', workspaceId: 'workspace-f', startTime: datetime('2024-01-01T00:00:00Z')})
                    CREATE (t2:Trace {id: 'trace-f2', name: 'Filter Test 2', projectId: 'project-f', workspaceId: 'workspace-f', startTime: datetime('2024-01-02T00:00:00Z')})
                    CREATE (t3:Trace {id: 'trace-f3', name: 'Filter Test 3', projectId: 'project-f', workspaceId: 'workspace-f', startTime: datetime('2024-01-03T00:00:00Z')})
                    """);
            
            // Query with filtering
            var result = session.run("""
                    MATCH (t:Trace)
                    WHERE t.projectId = 'project-f'
                      AND t.startTime >= datetime('2024-01-02T00:00:00Z')
                    RETURN t.id AS id
                    ORDER BY t.startTime
                    """);
            
            int count = 0;
            while (result.hasNext()) {
                count++;
                result.next();
            }
            assertEquals(2, count);
            
            // Clean up
            session.run("MATCH (t:Trace) WHERE t.projectId = 'project-f' DETACH DELETE t");
        }
    }

    @Test
    void testParentChildSpanRelationships() {
        // Test creating parent-child span relationships
        try (Session session = driver.session()) {
            // Create trace
            session.run("""
                    CREATE (t:Trace {
                        id: 'trace-parent',
                        name: 'Parent Test Trace',
                        projectId: 'test-project',
                        workspaceId: 'test-workspace'
                    })
                    """);
            
            // Create parent span
            session.run("""
                    MATCH (t:Trace {id: 'trace-parent'})
                    CREATE (s:Span {
                        id: 'span-parent',
                        name: 'Parent Span',
                        traceId: 'trace-parent',
                        projectId: 'test-project',
                        workspaceId: 'test-workspace'
                    })
                    CREATE (t)-[:HAS_SPAN]->(s)
                    """);
            
            // Create child span with PARENT_OF relationship
            session.run("""
                    MATCH (t:Trace {id: 'trace-parent'})
                    MATCH (ps:Span {id: 'span-parent'})
                    CREATE (s:Span {
                        id: 'span-child',
                        name: 'Child Span',
                        traceId: 'trace-parent',
                        parentSpanId: 'span-parent',
                        projectId: 'test-project',
                        workspaceId: 'test-workspace'
                    })
                    CREATE (t)-[:HAS_SPAN]->(s)
                    CREATE (ps)-[:PARENT_OF]->(s)
                    """);
            
            // Verify parent-child relationship
            var result = session.run("""
                    MATCH (ps:Span {id: 'span-parent'})-[:PARENT_OF]->(cs:Span)
                    RETURN cs.id AS childId
                    """);
            
            assertTrue(result.hasNext());
            assertEquals("span-child", result.next().get("childId").asString());
            
            // Clean up
            session.run("MATCH (t:Trace {id: 'trace-parent'}) DETACH DELETE t");
            session.run("MATCH (s:Span) WHERE s.traceId = 'trace-parent' DETACH DELETE s");
        }
    }
}

