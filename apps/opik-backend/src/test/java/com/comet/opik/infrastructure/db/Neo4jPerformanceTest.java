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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance benchmark tests for Neo4j operations.
 * These tests measure the performance of common database operations.
 */
@Testcontainers
public class Neo4jPerformanceTest {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.27.0-community")
            .withAdminPassword("testpassword")
            .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
            .withEnv("NEO4J_AUTH", "neo4j/testpassword")
            .withEnv("NEO4J_server_memory_heap_max__size", "2G")
            .withEnv("NEO4J_server_memory_pagecache_size", "1G");

    private static Driver driver;

    @BeforeAll
    static void setUp() {
        neo4jContainer.start();
        
        driver = GraphDatabase.driver(
                neo4jContainer.getBoltUrl(),
                AuthTokens.basic("neo4j", "testpassword")
        );
        
        // Create indexes for better performance
        try (Session session = driver.session()) {
            session.run("CREATE CONSTRAINT trace_id IF NOT EXISTS FOR (t:Trace) REQUIRE t.id IS UNIQUE");
            session.run("CREATE CONSTRAINT span_id IF NOT EXISTS FOR (s:Span) REQUIRE s.id IS UNIQUE");
            session.run("CREATE INDEX trace_project_start IF NOT EXISTS FOR (t:Trace) ON (t.projectId, t.startTime)");
            session.run("CREATE INDEX span_trace_start IF NOT EXISTS FOR (s:Span) ON (s.traceId, s.startTime)");
        }
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            // Clean up test data
            try (Session session = driver.session()) {
                session.run("MATCH (n) DETACH DELETE n");
            }
            driver.close();
        }
    }

    @Test
    void testBatchInsertPerformance() {
        System.out.println("\n=== Batch Insert Performance Test ===");
        
        int batchSize = 1000;
        List<Map<String, Object>> traces = new ArrayList<>();
        
        for (int i = 0; i < batchSize; i++) {
            Map<String, Object> trace = new HashMap<>();
            trace.put("id", "perf-trace-" + i);
            trace.put("name", "Performance Test Trace " + i);
            trace.put("projectId", "perf-project");
            trace.put("workspaceId", "perf-workspace");
            trace.put("startTime", Instant.now().toString());
            traces.add(trace);
        }
        
        try (Session session = driver.session()) {
            Instant start = Instant.now();
            
            session.run("""
                    UNWIND $traces AS traceData
                    CREATE (t:Trace {
                        id: traceData.id,
                        name: traceData.name,
                        projectId: traceData.projectId,
                        workspaceId: traceData.workspaceId,
                        startTime: datetime(traceData.startTime)
                    })
                    """, Map.of("traces", traces));
            
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            System.out.println("Inserted " + batchSize + " traces in " + duration.toMillis() + "ms");
            System.out.println("Throughput: " + (batchSize / (duration.toMillis() / 1000.0)) + " traces/second");
            
            // Verify all traces were created
            var result = session.run("MATCH (t:Trace) WHERE t.projectId = 'perf-project' RETURN count(t) AS count");
            int count = result.next().get("count").asInt();
            System.out.println("Verified " + count + " traces were inserted");
            assertTrue(count == batchSize);
            
            // Clean up
            session.run("MATCH (t:Trace) WHERE t.projectId = 'perf-project' DETACH DELETE t");
        }
    }

    @Test
    void testIndexedQueryPerformance() {
        System.out.println("\n=== Indexed Query Performance Test ===");
        
        // Create test data
        int dataSize = 10000;
        List<Map<String, Object>> traces = new ArrayList<>();
        
        for (int i = 0; i < dataSize; i++) {
            Map<String, Object> trace = new HashMap<>();
            trace.put("id", "query-trace-" + i);
            trace.put("name", "Query Test Trace " + i);
            trace.put("projectId", "query-project");
            trace.put("workspaceId", "query-workspace");
            trace.put("startTime", Instant.now().minusSeconds(i).toString());
            traces.add(trace);
        }
        
        try (Session session = driver.session()) {
            // Insert test data
            session.run("""
                    UNWIND $traces AS traceData
                    CREATE (t:Trace {
                        id: traceData.id,
                        name: traceData.name,
                        projectId: traceData.projectId,
                        workspaceId: traceData.workspaceId,
                        startTime: datetime(traceData.startTime)
                    })
                    """, Map.of("traces", traces));
            
            System.out.println("Created " + dataSize + " traces for query testing");
            
            // Test filtered query performance
            Instant start = Instant.now();
            
            var result = session.run("""
                    MATCH (t:Trace)
                    WHERE t.projectId = 'query-project'
                      AND t.startTime >= datetime($startTime)
                    RETURN count(t) AS count
                    """, Map.of("startTime", Instant.now().minusSeconds(5000).toString()));
            
            int count = result.next().get("count").asInt();
            
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            System.out.println("Query returned " + count + " traces in " + duration.toMillis() + "ms");
            assertTrue(duration.toMillis() < 1000, "Query should complete in less than 1 second");
            
            // Clean up
            session.run("MATCH (t:Trace) WHERE t.projectId = 'query-project' DETACH DELETE t");
        }
    }

    @Test
    void testRelationshipTraversalPerformance() {
        System.out.println("\n=== Relationship Traversal Performance Test ===");
        
        try (Session session = driver.session()) {
            // Create a trace with many spans
            int spanCount = 100;
            
            session.run("""
                    CREATE (t:Trace {
                        id: 'traversal-trace',
                        name: 'Traversal Test Trace',
                        projectId: 'traversal-project',
                        workspaceId: 'traversal-workspace'
                    })
                    """);
            
            // Create spans with relationships
            List<Map<String, Object>> spans = new ArrayList<>();
            for (int i = 0; i < spanCount; i++) {
                Map<String, Object> span = new HashMap<>();
                span.put("id", "traversal-span-" + i);
                span.put("name", "Span " + i);
                span.put("traceId", "traversal-trace");
                span.put("projectId", "traversal-project");
                span.put("workspaceId", "traversal-workspace");
                spans.add(span);
            }
            
            session.run("""
                    MATCH (t:Trace {id: 'traversal-trace'})
                    UNWIND $spans AS spanData
                    CREATE (s:Span {
                        id: spanData.id,
                        name: spanData.name,
                        traceId: spanData.traceId,
                        projectId: spanData.projectId,
                        workspaceId: spanData.workspaceId
                    })
                    CREATE (t)-[:HAS_SPAN]->(s)
                    """, Map.of("spans", spans));
            
            System.out.println("Created trace with " + spanCount + " spans");
            
            // Test relationship traversal performance
            Instant start = Instant.now();
            
            var result = session.run("""
                    MATCH (t:Trace {id: 'traversal-trace'})-[:HAS_SPAN]->(s:Span)
                    RETURN count(s) AS spanCount
                    """);
            
            int count = result.next().get("spanCount").asInt();
            
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            System.out.println("Traversed " + count + " spans in " + duration.toMillis() + "ms");
            assertTrue(count == spanCount);
            assertTrue(duration.toMillis() < 500, "Traversal should complete in less than 500ms");
            
            // Clean up
            session.run("MATCH (t:Trace {id: 'traversal-trace'}) DETACH DELETE t");
            session.run("MATCH (s:Span) WHERE s.traceId = 'traversal-trace' DETACH DELETE s");
        }
    }

    @Test
    void testUpdatePerformance() {
        System.out.println("\n=== Update Performance Test ===");
        
        try (Session session = driver.session()) {
            // Create test traces
            int traceCount = 1000;
            List<Map<String, Object>> traces = new ArrayList<>();
            
            for (int i = 0; i < traceCount; i++) {
                Map<String, Object> trace = new HashMap<>();
                trace.put("id", "update-trace-" + i);
                trace.put("name", "Update Test Trace " + i);
                trace.put("projectId", "update-project");
                trace.put("workspaceId", "update-workspace");
                trace.put("status", "pending");
                traces.add(trace);
            }
            
            session.run("""
                    UNWIND $traces AS traceData
                    CREATE (t:Trace {
                        id: traceData.id,
                        name: traceData.name,
                        projectId: traceData.projectId,
                        workspaceId: traceData.workspaceId,
                        status: traceData.status
                    })
                    """, Map.of("traces", traces));
            
            System.out.println("Created " + traceCount + " traces for update testing");
            
            // Test bulk update performance
            Instant start = Instant.now();
            
            session.run("""
                    MATCH (t:Trace)
                    WHERE t.projectId = 'update-project'
                    SET t.status = 'completed', t.updatedAt = datetime()
                    """);
            
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            System.out.println("Updated " + traceCount + " traces in " + duration.toMillis() + "ms");
            System.out.println("Throughput: " + (traceCount / (duration.toMillis() / 1000.0)) + " updates/second");
            
            // Verify updates
            var result = session.run("""
                    MATCH (t:Trace)
                    WHERE t.projectId = 'update-project' AND t.status = 'completed'
                    RETURN count(t) AS count
                    """);
            
            int count = result.next().get("count").asInt();
            System.out.println("Verified " + count + " traces were updated");
            assertTrue(count == traceCount);
            
            // Clean up
            session.run("MATCH (t:Trace) WHERE t.projectId = 'update-project' DETACH DELETE t");
        }
    }
}

