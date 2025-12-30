package com.comet.opik.domain;

import com.comet.opik.api.BiInformationResponse.BiInformation;
import com.comet.opik.api.ProjectStats;
import com.comet.opik.api.Trace;
import com.comet.opik.api.TraceDetails;
import com.comet.opik.api.TraceThread;
import com.comet.opik.api.TraceUpdate;
import com.comet.opik.infrastructure.db.Neo4jTransactionTemplate;
import com.comet.opik.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.r2dbc.spi.Connection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.comet.opik.api.Trace.TracePage;
import static com.comet.opik.api.TraceCountResponse.WorkspaceTraceCount;

/**
 * Neo4j implementation of TraceDAO using Cypher queries.
 * This replaces the ClickHouse R2DBC implementation with a graph-based approach.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TraceDAONeo4jImpl implements TraceDAO {

    private final Neo4jTransactionTemplate neo4jTemplate;

    @Override
    public Mono<UUID> insert(Trace trace, Connection connection) {
        log.info("Inserting trace with id '{}'", trace.getId());
        
        String cypher = """
                MATCH (p:Project {id: $projectId})
                CREATE (t:Trace {
                    id: $id,
                    projectId: $projectId,
                    workspaceId: $workspaceId,
                    name: $name,
                    startTime: datetime($startTime),
                    endTime: datetime($endTime),
                    input: $input,
                    output: $output,
                    metadata: $metadata,
                    tags: $tags,
                    lastUpdatedAt: datetime($lastUpdatedAt),
                    errorInfo: $errorInfo,
                    createdBy: $createdBy,
                    lastUpdatedBy: $lastUpdatedBy,
                    threadId: $threadId,
                    visibilityMode: $visibilityMode,
                    truncationThreshold: $truncationThreshold
                })
                CREATE (p)-[:HAS_TRACE]->(t)
                RETURN t.id as id
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", trace.getId().toString());
        params.put("projectId", trace.getProjectId().toString());
        params.put("workspaceId", trace.getWorkspaceId());
        params.put("name", trace.getName());
        params.put("startTime", trace.getStartTime() != null ? trace.getStartTime().toString() : null);
        params.put("endTime", trace.getEndTime() != null ? trace.getEndTime().toString() : null);
        params.put("input", JsonUtils.writeValueAsString(trace.getInput()));
        params.put("output", JsonUtils.writeValueAsString(trace.getOutput()));
        params.put("metadata", JsonUtils.writeValueAsString(trace.getMetadata()));
        params.put("tags", trace.getTags());
        params.put("lastUpdatedAt", trace.getLastUpdatedAt() != null ? trace.getLastUpdatedAt().toString() : Instant.now().toString());
        params.put("errorInfo", trace.getErrorInfo() != null ? JsonUtils.writeValueAsString(trace.getErrorInfo()) : null);
        params.put("createdBy", trace.getCreatedBy());
        params.put("lastUpdatedBy", trace.getLastUpdatedBy());
        params.put("threadId", trace.getThreadId());
        params.put("visibilityMode", trace.getVisibilityMode() != null ? trace.getVisibilityMode().name() : "DEFAULT");
        params.put("truncationThreshold", trace.getTruncationThreshold());
        
        return neo4jTemplate.executeWrite(cypher, params, record -> 
                UUID.fromString(record.get("id").asString()));
    }

    @Override
    public Mono<Void> update(TraceUpdate traceUpdate, UUID id, Connection connection) {
        log.info("Updating trace with id '{}'", id);
        
        String cypher = """
                MATCH (t:Trace {id: $id})
                SET t.name = COALESCE($name, t.name),
                    t.endTime = COALESCE(datetime($endTime), t.endTime),
                    t.input = COALESCE($input, t.input),
                    t.output = COALESCE($output, t.output),
                    t.metadata = COALESCE($metadata, t.metadata),
                    t.tags = COALESCE($tags, t.tags),
                    t.lastUpdatedAt = datetime($lastUpdatedAt),
                    t.errorInfo = COALESCE($errorInfo, t.errorInfo),
                    t.lastUpdatedBy = COALESCE($lastUpdatedBy, t.lastUpdatedBy)
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", id.toString());
        params.put("name", traceUpdate.name());
        params.put("endTime", traceUpdate.endTime() != null ? traceUpdate.endTime().toString() : null);
        params.put("input", traceUpdate.input() != null ? JsonUtils.writeValueAsString(traceUpdate.input()) : null);
        params.put("output", traceUpdate.output() != null ? JsonUtils.writeValueAsString(traceUpdate.output()) : null);
        params.put("metadata", traceUpdate.metadata() != null ? JsonUtils.writeValueAsString(traceUpdate.metadata()) : null);
        params.put("tags", traceUpdate.tags());
        params.put("lastUpdatedAt", Instant.now().toString());
        params.put("errorInfo", traceUpdate.errorInfo() != null ? JsonUtils.writeValueAsString(traceUpdate.errorInfo()) : null);
        params.put("lastUpdatedBy", traceUpdate.lastUpdatedBy());
        
        return neo4jTemplate.executeWriteVoid(cypher, params);
    }

    @Override
    public Mono<Void> delete(Set<UUID> ids, UUID projectId, Connection connection) {
        log.info("Deleting {} traces from project '{}'", ids.size(), projectId);
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.id IN $ids AND t.projectId = $projectId
                DETACH DELETE t
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("ids", ids.stream().map(UUID::toString).collect(Collectors.toList()));
        params.put("projectId", projectId.toString());
        
        return neo4jTemplate.executeWriteVoid(cypher, params);
    }

    @Override
    public Mono<Trace> findById(UUID id, Connection connection) {
        log.info("Finding trace by id '{}'", id);
        
        String cypher = """
                MATCH (t:Trace {id: $id})
                RETURN t
                """;
        
        Map<String, Object> params = Map.of("id", id.toString());
        
        return neo4jTemplate.executeRead(cypher, params, record -> mapToTrace(record));
    }

    @Override
    public Flux<Trace> findByIds(List<UUID> ids, Connection connection) {
        log.info("Finding {} traces by ids", ids.size());
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.id IN $ids
                RETURN t
                """;
        
        Map<String, Object> params = Map.of("ids", ids.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> mapToTrace(record));
    }

    @Override
    public Mono<TraceDetails> getTraceDetailsById(UUID id, Connection connection) {
        log.info("Getting trace details for id '{}'", id);
        
        // TODO: Implement full trace details with spans and feedback
        return findById(id, connection)
                .map(trace -> TraceDetails.builder()
                        .trace(trace)
                        .build());
    }

    @Override
    public Mono<TracePage> find(int size, int page, TraceSearchCriteria traceSearchCriteria, Connection connection) {
        log.info("Finding traces with size '{}', page '{}', criteria '{}'", size, page, traceSearchCriteria);
        
        // Build dynamic query based on search criteria
        StringBuilder cypherBuilder = new StringBuilder("""
                MATCH (t:Trace)
                WHERE t.projectId = $projectId
                """);
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", traceSearchCriteria.projectId().toString());
        
        // Add filters
        if (traceSearchCriteria.filters() != null && !traceSearchCriteria.filters().isEmpty()) {
            // TODO: Implement dynamic filter building for Cypher
            log.warn("Filter support not yet implemented for Neo4j");
        }
        
        cypherBuilder.append(" RETURN t ORDER BY t.startTime DESC SKIP $skip LIMIT $limit");
        params.put("skip", (long) page * size);
        params.put("limit", (long) size);
        
        Flux<Trace> traces = neo4jTemplate.executeReadFlux(cypherBuilder.toString(), params, 
                record -> mapToTrace(record));
        
        // Count total
        String countCypher = """
                MATCH (t:Trace)
                WHERE t.projectId = $projectId
                RETURN count(t) as total
                """;
        
        Mono<Long> total = neo4jTemplate.executeRead(countCypher, 
                Map.of("projectId", traceSearchCriteria.projectId().toString()), 
                record -> record.get("total").asLong());
        
        return Mono.zip(traces.collectList(), total)
                .map(tuple -> new TracePage(tuple.getT1(), page, size, tuple.getT2()));
    }

    @Override
    public Mono<Void> partialInsert(UUID projectId, TraceUpdate traceUpdate, UUID traceId, Connection connection) {
        log.info("Partial insert for trace '{}'", traceId);
        
        // For partial insert, we merge the data
        String cypher = """
                MATCH (p:Project {id: $projectId})
                MERGE (t:Trace {id: $traceId})
                ON CREATE SET
                    t.projectId = $projectId,
                    t.workspaceId = $workspaceId,
                    t.name = $name,
                    t.startTime = datetime($startTime),
                    t.endTime = datetime($endTime),
                    t.input = $input,
                    t.output = $output,
                    t.metadata = $metadata,
                    t.tags = $tags,
                    t.lastUpdatedAt = datetime($lastUpdatedAt),
                    t.errorInfo = $errorInfo,
                    t.createdBy = $createdBy,
                    t.lastUpdatedBy = $lastUpdatedBy
                ON MATCH SET
                    t.name = COALESCE($name, t.name),
                    t.endTime = COALESCE(datetime($endTime), t.endTime),
                    t.input = COALESCE($input, t.input),
                    t.output = COALESCE($output, t.output),
                    t.metadata = COALESCE($metadata, t.metadata),
                    t.tags = COALESCE($tags, t.tags),
                    t.lastUpdatedAt = datetime($lastUpdatedAt),
                    t.errorInfo = COALESCE($errorInfo, t.errorInfo),
                    t.lastUpdatedBy = COALESCE($lastUpdatedBy, t.lastUpdatedBy)
                MERGE (p)-[:HAS_TRACE]->(t)
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId.toString());
        params.put("traceId", traceId.toString());
        params.put("workspaceId", traceUpdate.workspaceId());
        params.put("name", traceUpdate.name());
        params.put("startTime", traceUpdate.startTime() != null ? traceUpdate.startTime().toString() : Instant.now().toString());
        params.put("endTime", traceUpdate.endTime() != null ? traceUpdate.endTime().toString() : null);
        params.put("input", traceUpdate.input() != null ? JsonUtils.writeValueAsString(traceUpdate.input()) : null);
        params.put("output", traceUpdate.output() != null ? JsonUtils.writeValueAsString(traceUpdate.output()) : null);
        params.put("metadata", traceUpdate.metadata() != null ? JsonUtils.writeValueAsString(traceUpdate.metadata()) : null);
        params.put("tags", traceUpdate.tags());
        params.put("lastUpdatedAt", Instant.now().toString());
        params.put("errorInfo", traceUpdate.errorInfo() != null ? JsonUtils.writeValueAsString(traceUpdate.errorInfo()) : null);
        params.put("createdBy", traceUpdate.createdBy());
        params.put("lastUpdatedBy", traceUpdate.lastUpdatedBy());
        
        return neo4jTemplate.executeWriteVoid(cypher, params);
    }

    @Override
    public Mono<List<WorkspaceAndResourceId>> getTraceWorkspace(Set<UUID> traceIds, Connection connection) {
        log.info("Getting workspace for {} traces", traceIds.size());
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.id IN $traceIds
                RETURN t.id as resourceId, t.workspaceId as workspaceId
                """;
        
        Map<String, Object> params = Map.of("traceIds", 
                traceIds.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> 
                new WorkspaceAndResourceId(
                        record.get("workspaceId").asString(),
                        UUID.fromString(record.get("resourceId").asString())
                )
        ).collectList();
    }

    @Override
    public Mono<Long> batchInsert(List<Trace> traces, Connection connection) {
        log.info("Batch inserting {} traces", traces.size());
        
        // Use UNWIND for efficient batch insertion in Neo4j
        String cypher = """
                UNWIND $traces AS traceData
                MATCH (p:Project {id: traceData.projectId})
                CREATE (t:Trace {
                    id: traceData.id,
                    projectId: traceData.projectId,
                    workspaceId: traceData.workspaceId,
                    name: traceData.name,
                    startTime: datetime(traceData.startTime),
                    endTime: datetime(traceData.endTime),
                    input: traceData.input,
                    output: traceData.output,
                    metadata: traceData.metadata,
                    tags: traceData.tags,
                    lastUpdatedAt: datetime(traceData.lastUpdatedAt),
                    errorInfo: traceData.errorInfo,
                    createdBy: traceData.createdBy,
                    lastUpdatedBy: traceData.lastUpdatedBy,
                    threadId: traceData.threadId,
                    visibilityMode: traceData.visibilityMode,
                    truncationThreshold: traceData.truncationThreshold
                })
                CREATE (p)-[:HAS_TRACE]->(t)
                """;
        
        List<Map<String, Object>> traceData = traces.stream().map(trace -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", trace.getId().toString());
            data.put("projectId", trace.getProjectId().toString());
            data.put("workspaceId", trace.getWorkspaceId());
            data.put("name", trace.getName());
            data.put("startTime", trace.getStartTime() != null ? trace.getStartTime().toString() : null);
            data.put("endTime", trace.getEndTime() != null ? trace.getEndTime().toString() : null);
            data.put("input", JsonUtils.writeValueAsString(trace.getInput()));
            data.put("output", JsonUtils.writeValueAsString(trace.getOutput()));
            data.put("metadata", JsonUtils.writeValueAsString(trace.getMetadata()));
            data.put("tags", trace.getTags());
            data.put("lastUpdatedAt", trace.getLastUpdatedAt() != null ? trace.getLastUpdatedAt().toString() : Instant.now().toString());
            data.put("errorInfo", trace.getErrorInfo() != null ? JsonUtils.writeValueAsString(trace.getErrorInfo()) : null);
            data.put("createdBy", trace.getCreatedBy());
            data.put("lastUpdatedBy", trace.getLastUpdatedBy());
            data.put("threadId", trace.getThreadId());
            data.put("visibilityMode", trace.getVisibilityMode() != null ? trace.getVisibilityMode().name() : "DEFAULT");
            data.put("truncationThreshold", trace.getTruncationThreshold());
            return data;
        }).collect(Collectors.toList());
        
        Map<String, Object> params = Map.of("traces", traceData);
        
        return neo4jTemplate.executeWriteVoid(cypher, params)
                .thenReturn((long) traces.size());
    }

    // Stub implementations for remaining methods - these need full implementation
    @Override
    public Flux<WorkspaceTraceCount> countTracesPerWorkspace(Map<UUID, Instant> excludedProjectIds) {
        log.warn("countTracesPerWorkspace not yet fully implemented for Neo4j");
        return Flux.empty();
    }

    @Override
    public Mono<Map<UUID, Instant>> getLastUpdatedTraceAt(Set<UUID> projectIds, String workspaceId, Connection connection) {
        log.warn("getLastUpdatedTraceAt not yet fully implemented for Neo4j");
        return Mono.just(Map.of());
    }

    @Override
    public Mono<UUID> getProjectIdFromTrace(UUID traceId) {
        String cypher = "MATCH (t:Trace {id: $traceId}) RETURN t.projectId as projectId";
        return neo4jTemplate.executeRead(cypher, Map.of("traceId", traceId.toString()), 
                record -> UUID.fromString(record.get("projectId").asString()));
    }

    @Override
    public Flux<BiInformation> getTraceBIInformation(Map<UUID, Instant> excludedProjectIds) {
        log.warn("getTraceBIInformation not yet fully implemented for Neo4j");
        return Flux.empty();
    }

    @Override
    public Mono<ProjectStats> getStats(TraceSearchCriteria criteria) {
        log.warn("getStats not yet fully implemented for Neo4j");
        return Mono.just(new ProjectStats(0, 0, 0));
    }

    @Override
    public Mono<Long> getDailyTraces(Map<UUID, Instant> excludedProjectIds) {
        log.warn("getDailyTraces not yet fully implemented for Neo4j");
        return Mono.just(0L);
    }

    @Override
    public Mono<Map<UUID, ProjectStats>> getStatsByProjectIds(List<UUID> projectIds, String workspaceId) {
        log.warn("getStatsByProjectIds not yet fully implemented for Neo4j");
        return Mono.just(Map.of());
    }

    @Override
    public Mono<Set<UUID>> getTraceIdsByThreadIds(UUID projectId, List<String> threadIds, Connection connection) {
        String cypher = """
                MATCH (t:Trace)
                WHERE t.projectId = $projectId AND t.threadId IN $threadIds
                RETURN t.id as id
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId.toString());
        params.put("threadIds", threadIds);
        
        return neo4jTemplate.executeReadFlux(cypher, params, 
                record -> UUID.fromString(record.get("id").asString()))
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<Trace> getPartialById(UUID id) {
        String cypher = "MATCH (t:Trace {id: $id}) RETURN t.startTime as startTime, t.projectId as projectId";
        return neo4jTemplate.executeRead(cypher, Map.of("id", id.toString()), 
                record -> Trace.builder()
                        .startTime(Instant.parse(record.get("startTime").asString()))
                        .projectId(UUID.fromString(record.get("projectId").asString()))
                        .build());
    }

    @Override
    public Flux<Trace> search(int limit, TraceSearchCriteria criteria) {
        log.info("Searching traces with limit '{}', criteria '{}'", limit, criteria);
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.projectId = $projectId
                RETURN t
                ORDER BY t.startTime DESC
                LIMIT $limit
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", criteria.projectId().toString());
        params.put("limit", (long) limit);
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> mapToTrace(record));
    }

    @Override
    public Mono<Long> countTraces(Set<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Mono.just(0L);
        }
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.projectId IN $projectIds
                RETURN count(t) as count
                """;
        
        Map<String, Object> params = Map.of("projectIds", 
                projectIds.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeRead(cypher, params, record -> record.get("count").asLong());
    }

    @Override
    public Mono<List<TraceThread>> getMinimalThreadInfoByIds(UUID projectId, Set<String> threadId) {
        log.warn("getMinimalThreadInfoByIds not yet fully implemented for Neo4j");
        return Mono.just(List.of());
    }

    @Override
    public Mono<Void> bulkUpdate(@NonNull Set<UUID> ids, @NonNull TraceUpdate update, boolean mergeTags) {
        log.info("Bulk updating {} traces", ids.size());
        
        String cypher = """
                MATCH (t:Trace)
                WHERE t.id IN $ids
                SET t.name = COALESCE($name, t.name),
                    t.endTime = COALESCE(datetime($endTime), t.endTime),
                    t.input = COALESCE($input, t.input),
                    t.output = COALESCE($output, t.output),
                    t.metadata = COALESCE($metadata, t.metadata),
                    t.tags = CASE WHEN $mergeTags THEN t.tags + $tags ELSE COALESCE($tags, t.tags) END,
                    t.lastUpdatedAt = datetime($lastUpdatedAt),
                    t.errorInfo = COALESCE($errorInfo, t.errorInfo)
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("ids", ids.stream().map(UUID::toString).collect(Collectors.toList()));
        params.put("name", update.name());
        params.put("endTime", update.endTime() != null ? update.endTime().toString() : null);
        params.put("input", update.input() != null ? JsonUtils.writeValueAsString(update.input()) : null);
        params.put("output", update.output() != null ? JsonUtils.writeValueAsString(update.output()) : null);
        params.put("metadata", update.metadata() != null ? JsonUtils.writeValueAsString(update.metadata()) : null);
        params.put("tags", update.tags());
        params.put("mergeTags", mergeTags);
        params.put("lastUpdatedAt", Instant.now().toString());
        params.put("errorInfo", update.errorInfo() != null ? JsonUtils.writeValueAsString(update.errorInfo()) : null);
        
        return neo4jTemplate.executeWriteVoid(cypher, params);
    }

    /**
     * Helper method to map Neo4j record to Trace object.
     */
    private Trace mapToTrace(org.neo4j.driver.Record record) {
        var node = record.get("t").asNode();
        
        return Trace.builder()
                .id(UUID.fromString(node.get("id").asString()))
                .projectId(UUID.fromString(node.get("projectId").asString()))
                .workspaceId(node.get("workspaceId").asString())
                .name(node.get("name").asString())
                .startTime(node.get("startTime").asString() != null ? 
                        Instant.parse(node.get("startTime").asString()) : null)
                .endTime(node.get("endTime").asString() != null ? 
                        Instant.parse(node.get("endTime").asString()) : null)
                .input(node.get("input").asString() != null ? 
                        JsonUtils.readTree(node.get("input").asString()) : null)
                .output(node.get("output").asString() != null ? 
                        JsonUtils.readTree(node.get("output").asString()) : null)
                .metadata(node.get("metadata").asString() != null ? 
                        JsonUtils.readTree(node.get("metadata").asString()) : null)
                .tags(node.get("tags").asList(Object::toString))
                .lastUpdatedAt(node.get("lastUpdatedAt").asString() != null ? 
                        Instant.parse(node.get("lastUpdatedAt").asString()) : null)
                .createdBy(node.get("createdBy").asString())
                .lastUpdatedBy(node.get("lastUpdatedBy").asString())
                .threadId(node.get("threadId").asString(null))
                .build();
    }
}

