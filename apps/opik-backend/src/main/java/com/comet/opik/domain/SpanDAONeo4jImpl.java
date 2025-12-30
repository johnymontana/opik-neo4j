package com.comet.opik.domain;

import com.comet.opik.api.BiInformationResponse;
import com.comet.opik.api.ProjectStats;
import com.comet.opik.api.Span;
import com.comet.opik.api.SpanUpdate;
import com.comet.opik.api.SpansCountResponse;
import com.comet.opik.infrastructure.db.Neo4jTransactionTemplate;
import com.comet.opik.utils.JsonUtils;
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

import static com.comet.opik.api.Span.SpanPage;

/**
 * Neo4j implementation of SpanDAO using Cypher queries.
 * This replaces the ClickHouse R2DBC implementation with a graph-based approach.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SpanDAONeo4jImpl {

    private final Neo4jTransactionTemplate neo4jTemplate;

    public Mono<Void> insert(@NonNull Span span) {
        log.info("Inserting span with id '{}'", span.getId());
        
        String cypher = """
                MATCH (t:Trace {id: $traceId})
                CREATE (s:Span {
                    id: $id,
                    projectId: $projectId,
                    workspaceId: $workspaceId,
                    traceId: $traceId,
                    parentSpanId: $parentSpanId,
                    name: $name,
                    type: $type,
                    startTime: datetime($startTime),
                    endTime: datetime($endTime),
                    input: $input,
                    output: $output,
                    metadata: $metadata,
                    model: $model,
                    provider: $provider,
                    totalEstimatedCost: $totalEstimatedCost,
                    tags: $tags,
                    usage: $usage,
                    lastUpdatedAt: datetime($lastUpdatedAt),
                    errorInfo: $errorInfo,
                    createdBy: $createdBy,
                    lastUpdatedBy: $lastUpdatedBy,
                    truncationThreshold: $truncationThreshold
                })
                CREATE (t)-[:HAS_SPAN]->(s)
                WITH s
                OPTIONAL MATCH (ps:Span {id: $parentSpanId})
                WHERE $parentSpanId IS NOT NULL
                FOREACH (_ IN CASE WHEN ps IS NOT NULL THEN [1] ELSE [] END |
                    CREATE (ps)-[:PARENT_OF]->(s)
                )
                """;
        
        Map<String, Object> params = buildSpanParams(span);
        
        return neo4jTemplate.executeWriteVoid(cypher, params);
    }

    public Mono<Long> batchInsert(@NonNull List<Span> spans) {
        log.info("Batch inserting {} spans", spans.size());
        
        // Use UNWIND for efficient batch insertion in Neo4j
        String cypher = """
                UNWIND $spans AS spanData
                MATCH (t:Trace {id: spanData.traceId})
                CREATE (s:Span {
                    id: spanData.id,
                    projectId: spanData.projectId,
                    workspaceId: spanData.workspaceId,
                    traceId: spanData.traceId,
                    parentSpanId: spanData.parentSpanId,
                    name: spanData.name,
                    type: spanData.type,
                    startTime: datetime(spanData.startTime),
                    endTime: datetime(spanData.endTime),
                    input: spanData.input,
                    output: spanData.output,
                    metadata: spanData.metadata,
                    model: spanData.model,
                    provider: spanData.provider,
                    totalEstimatedCost: spanData.totalEstimatedCost,
                    tags: spanData.tags,
                    usage: spanData.usage,
                    lastUpdatedAt: datetime(spanData.lastUpdatedAt),
                    errorInfo: spanData.errorInfo,
                    createdBy: spanData.createdBy,
                    lastUpdatedBy: spanData.lastUpdatedBy,
                    truncationThreshold: spanData.truncationThreshold
                })
                CREATE (t)-[:HAS_SPAN]->(s)
                WITH s, spanData
                OPTIONAL MATCH (ps:Span {id: spanData.parentSpanId})
                WHERE spanData.parentSpanId IS NOT NULL
                FOREACH (_ IN CASE WHEN ps IS NOT NULL THEN [1] ELSE [] END |
                    CREATE (ps)-[:PARENT_OF]->(s)
                )
                """;
        
        List<Map<String, Object>> spanData = spans.stream()
                .map(this::buildSpanParamsForBatch)
                .collect(Collectors.toList());
        
        Map<String, Object> params = Map.of("spans", spanData);
        
        return neo4jTemplate.executeWriteVoid(cypher, params)
                .thenReturn((long) spans.size());
    }

    public Mono<Long> update(@NonNull UUID id, @NonNull SpanUpdate spanUpdate, Span existingSpan) {
        log.info("Updating span with id '{}'", id);
        
        String cypher = """
                MATCH (s:Span {id: $id})
                SET s.name = COALESCE($name, s.name),
                    s.endTime = COALESCE(datetime($endTime), s.endTime),
                    s.input = COALESCE($input, s.input),
                    s.output = COALESCE($output, s.output),
                    s.metadata = COALESCE($metadata, s.metadata),
                    s.model = COALESCE($model, s.model),
                    s.provider = COALESCE($provider, s.provider),
                    s.tags = COALESCE($tags, s.tags),
                    s.usage = COALESCE($usage, s.usage),
                    s.lastUpdatedAt = datetime($lastUpdatedAt),
                    s.errorInfo = COALESCE($errorInfo, s.errorInfo),
                    s.lastUpdatedBy = COALESCE($lastUpdatedBy, s.lastUpdatedBy)
                RETURN count(s) as count
                """;
        
        Map<String, Object> params = buildSpanUpdateParams(id, spanUpdate);
        
        return neo4jTemplate.executeWrite(cypher, params, record -> record.get("count").asLong());
    }

    public Mono<Long> partialInsert(@NonNull UUID id, @NonNull UUID projectId, @NonNull SpanUpdate spanUpdate) {
        log.info("Partial insert for span '{}'", id);
        
        String cypher = """
                MATCH (t:Trace {id: $traceId})
                MERGE (s:Span {id: $id})
                ON CREATE SET
                    s.projectId = $projectId,
                    s.workspaceId = $workspaceId,
                    s.traceId = $traceId,
                    s.parentSpanId = $parentSpanId,
                    s.name = $name,
                    s.type = $type,
                    s.startTime = datetime($startTime),
                    s.endTime = datetime($endTime),
                    s.input = $input,
                    s.output = $output,
                    s.metadata = $metadata,
                    s.model = $model,
                    s.provider = $provider,
                    s.tags = $tags,
                    s.usage = $usage,
                    s.lastUpdatedAt = datetime($lastUpdatedAt),
                    s.errorInfo = $errorInfo,
                    s.createdBy = $createdBy,
                    s.lastUpdatedBy = $lastUpdatedBy
                ON MATCH SET
                    s.name = COALESCE($name, s.name),
                    s.endTime = COALESCE(datetime($endTime), s.endTime),
                    s.input = COALESCE($input, s.input),
                    s.output = COALESCE($output, s.output),
                    s.metadata = COALESCE($metadata, s.metadata),
                    s.tags = COALESCE($tags, s.tags),
                    s.usage = COALESCE($usage, s.usage),
                    s.lastUpdatedAt = datetime($lastUpdatedAt),
                    s.errorInfo = COALESCE($errorInfo, s.errorInfo),
                    s.lastUpdatedBy = COALESCE($lastUpdatedBy, s.lastUpdatedBy)
                MERGE (t)-[:HAS_SPAN]->(s)
                RETURN count(s) as count
                """;
        
        Map<String, Object> params = buildSpanUpdateParams(id, spanUpdate);
        params.put("projectId", projectId.toString());
        
        return neo4jTemplate.executeWrite(cypher, params, record -> record.get("count").asLong());
    }

    public Mono<Span> getById(@NonNull UUID id) {
        log.info("Getting span by id '{}'", id);
        
        String cypher = "MATCH (s:Span {id: $id}) RETURN s";
        Map<String, Object> params = Map.of("id", id.toString());
        
        return neo4jTemplate.executeRead(cypher, params, record -> mapToSpan(record));
    }

    public Mono<Span> getOnlySpanDataById(@NonNull UUID id, @NonNull UUID projectId) {
        log.info("Getting only span data by id '{}' for project '{}'", id, projectId);
        
        String cypher = "MATCH (s:Span {id: $id, projectId: $projectId}) RETURN s";
        Map<String, Object> params = Map.of("id", id.toString(), "projectId", projectId.toString());
        
        return neo4jTemplate.executeRead(cypher, params, record -> mapToSpan(record));
    }

    public Mono<Span> getPartialById(@NonNull UUID id) {
        log.info("Getting partial span by id '{}'", id);
        
        String cypher = """
                MATCH (s:Span {id: $id})
                RETURN s.startTime as startTime, s.projectId as projectId, s.traceId as traceId
                """;
        
        return neo4jTemplate.executeRead(cypher, Map.of("id", id.toString()), 
                record -> Span.builder()
                        .startTime(Instant.parse(record.get("startTime").asString()))
                        .projectId(UUID.fromString(record.get("projectId").asString()))
                        .traceId(UUID.fromString(record.get("traceId").asString()))
                        .build());
    }

    public Flux<Span> getByTraceIds(@NonNull Set<UUID> traceIds) {
        log.info("Getting spans for {} traces", traceIds.size());
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.traceId IN $traceIds
                RETURN s
                ORDER BY s.startTime
                """;
        
        Map<String, Object> params = Map.of("traceIds", 
                traceIds.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> mapToSpan(record));
    }

    public Flux<Span> getByIds(@NonNull Set<UUID> ids) {
        log.info("Getting {} spans by ids", ids.size());
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.id IN $ids
                RETURN s
                """;
        
        Map<String, Object> params = Map.of("ids", 
                ids.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> mapToSpan(record));
    }

    public Mono<Long> deleteByTraceIds(Set<UUID> traceIds, UUID projectId) {
        log.info("Deleting spans for {} traces in project '{}'", traceIds.size(), projectId);
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.traceId IN $traceIds AND s.projectId = $projectId
                WITH s, count(s) as spanCount
                DETACH DELETE s
                RETURN spanCount
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("traceIds", traceIds.stream().map(UUID::toString).collect(Collectors.toList()));
        params.put("projectId", projectId.toString());
        
        return neo4jTemplate.executeWrite(cypher, params, record -> record.get("spanCount").asLong());
    }

    public Mono<SpanPage> find(int page, int size, @NonNull SpanSearchCriteria spanSearchCriteria) {
        log.info("Finding spans with page '{}', size '{}', criteria '{}'", page, size, spanSearchCriteria);
        
        StringBuilder cypherBuilder = new StringBuilder("""
                MATCH (s:Span)
                WHERE s.projectId = $projectId
                """);
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", spanSearchCriteria.projectId().toString());
        
        // Add filters if provided
        if (spanSearchCriteria.traceId() != null) {
            cypherBuilder.append(" AND s.traceId = $traceId");
            params.put("traceId", spanSearchCriteria.traceId().toString());
        }
        
        cypherBuilder.append(" RETURN s ORDER BY s.startTime DESC SKIP $skip LIMIT $limit");
        params.put("skip", (long) page * size);
        params.put("limit", (long) size);
        
        Flux<Span> spans = neo4jTemplate.executeReadFlux(cypherBuilder.toString(), params, 
                record -> mapToSpan(record));
        
        // Count total
        String countCypher = """
                MATCH (s:Span)
                WHERE s.projectId = $projectId
                """ + (spanSearchCriteria.traceId() != null ? " AND s.traceId = $traceId" : "") + """
                RETURN count(s) as total
                """;
        
        Mono<Long> total = neo4jTemplate.executeRead(countCypher, params, 
                record -> record.get("total").asLong());
        
        return Mono.zip(spans.collectList(), total)
                .map(tuple -> new SpanPage(tuple.getT1(), page, size, tuple.getT2()));
    }

    public Flux<Span> search(int limit, @NonNull SpanSearchCriteria criteria) {
        log.info("Searching spans with limit '{}', criteria '{}'", limit, criteria);
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.projectId = $projectId
                RETURN s
                ORDER BY s.startTime DESC
                LIMIT $limit
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", criteria.projectId().toString());
        params.put("limit", (long) limit);
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> mapToSpan(record));
    }

    public Mono<List<WorkspaceAndResourceId>> getSpanWorkspace(@NonNull Set<UUID> spanIds) {
        log.info("Getting workspace for {} spans", spanIds.size());
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.id IN $spanIds
                RETURN s.id as resourceId, s.workspaceId as workspaceId
                """;
        
        Map<String, Object> params = Map.of("spanIds", 
                spanIds.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, record -> 
                new WorkspaceAndResourceId(
                        record.get("workspaceId").asString(),
                        UUID.fromString(record.get("resourceId").asString())
                )
        ).collectList();
    }

    public Mono<UUID> getProjectIdFromSpan(@NonNull UUID spanId) {
        String cypher = "MATCH (s:Span {id: $spanId}) RETURN s.projectId as projectId";
        return neo4jTemplate.executeRead(cypher, Map.of("spanId", spanId.toString()), 
                record -> UUID.fromString(record.get("projectId").asString()));
    }

    public Mono<ProjectStats> getStats(@NonNull SpanSearchCriteria searchCriteria) {
        log.warn("getStats not yet fully implemented for Neo4j");
        return Mono.just(new ProjectStats(0, 0, 0));
    }

    public Mono<Set<UUID>> getSpanIdsForTraces(@NonNull Set<UUID> traceIds) {
        log.info("Getting span IDs for {} traces", traceIds.size());
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.traceId IN $traceIds
                RETURN s.id as id
                """;
        
        Map<String, Object> params = Map.of("traceIds", 
                traceIds.stream().map(UUID::toString).collect(Collectors.toList()));
        
        return neo4jTemplate.executeReadFlux(cypher, params, 
                record -> UUID.fromString(record.get("id").asString()))
                .collect(Collectors.toSet());
    }

    public Flux<SpansCountResponse.WorkspaceSpansCount> countSpansPerWorkspace(
            Map<UUID, Instant> excludedProjectIds) {
        log.warn("countSpansPerWorkspace not yet fully implemented for Neo4j");
        return Flux.empty();
    }

    public Flux<BiInformationResponse.BiInformation> getSpanBIInformation(
            Map<UUID, Instant> excludedProjectIds) {
        log.warn("getSpanBIInformation not yet fully implemented for Neo4j");
        return Flux.empty();
    }

    public Mono<Void> bulkUpdate(@NonNull Set<UUID> ids, @NonNull SpanUpdate update, boolean mergeTags) {
        log.info("Bulk updating {} spans", ids.size());
        
        String cypher = """
                MATCH (s:Span)
                WHERE s.id IN $ids
                SET s.name = COALESCE($name, s.name),
                    s.endTime = COALESCE(datetime($endTime), s.endTime),
                    s.input = COALESCE($input, s.input),
                    s.output = COALESCE($output, s.output),
                    s.metadata = COALESCE($metadata, s.metadata),
                    s.tags = CASE WHEN $mergeTags THEN s.tags + $tags ELSE COALESCE($tags, s.tags) END,
                    s.lastUpdatedAt = datetime($lastUpdatedAt),
                    s.errorInfo = COALESCE($errorInfo, s.errorInfo)
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
     * Helper method to build span parameters for insertion.
     */
    private Map<String, Object> buildSpanParams(Span span) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", span.getId().toString());
        params.put("projectId", span.getProjectId().toString());
        params.put("workspaceId", span.getWorkspaceId());
        params.put("traceId", span.getTraceId().toString());
        params.put("parentSpanId", span.getParentSpanId() != null ? span.getParentSpanId().toString() : null);
        params.put("name", span.getName());
        params.put("type", span.getType() != null ? span.getType().name() : null);
        params.put("startTime", span.getStartTime() != null ? span.getStartTime().toString() : null);
        params.put("endTime", span.getEndTime() != null ? span.getEndTime().toString() : null);
        params.put("input", span.getInput() != null ? JsonUtils.writeValueAsString(span.getInput()) : null);
        params.put("output", span.getOutput() != null ? JsonUtils.writeValueAsString(span.getOutput()) : null);
        params.put("metadata", span.getMetadata() != null ? JsonUtils.writeValueAsString(span.getMetadata()) : null);
        params.put("model", span.getModel());
        params.put("provider", span.getProvider());
        params.put("totalEstimatedCost", span.getTotalEstimatedCost() != null ? span.getTotalEstimatedCost().doubleValue() : null);
        params.put("tags", span.getTags());
        params.put("usage", span.getUsage() != null ? JsonUtils.writeValueAsString(span.getUsage()) : null);
        params.put("lastUpdatedAt", span.getLastUpdatedAt() != null ? span.getLastUpdatedAt().toString() : Instant.now().toString());
        params.put("errorInfo", span.getErrorInfo() != null ? JsonUtils.writeValueAsString(span.getErrorInfo()) : null);
        params.put("createdBy", span.getCreatedBy());
        params.put("lastUpdatedBy", span.getLastUpdatedBy());
        params.put("truncationThreshold", span.getTruncationThreshold());
        return params;
    }

    /**
     * Helper method to build span parameters for batch insertion.
     */
    private Map<String, Object> buildSpanParamsForBatch(Span span) {
        // Reuse the same logic
        return buildSpanParams(span);
    }

    /**
     * Helper method to build span update parameters.
     */
    private Map<String, Object> buildSpanUpdateParams(UUID id, SpanUpdate update) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id.toString());
        params.put("name", update.name());
        params.put("type", update.type() != null ? update.type().name() : null);
        params.put("endTime", update.endTime() != null ? update.endTime().toString() : null);
        params.put("input", update.input() != null ? JsonUtils.writeValueAsString(update.input()) : null);
        params.put("output", update.output() != null ? JsonUtils.writeValueAsString(update.output()) : null);
        params.put("metadata", update.metadata() != null ? JsonUtils.writeValueAsString(update.metadata()) : null);
        params.put("model", update.model());
        params.put("provider", update.provider());
        params.put("tags", update.tags());
        params.put("usage", update.usage() != null ? JsonUtils.writeValueAsString(update.usage()) : null);
        params.put("lastUpdatedAt", Instant.now().toString());
        params.put("errorInfo", update.errorInfo() != null ? JsonUtils.writeValueAsString(update.errorInfo()) : null);
        params.put("lastUpdatedBy", update.lastUpdatedBy());
        params.put("traceId", update.traceId() != null ? update.traceId().toString() : null);
        params.put("parentSpanId", update.parentSpanId() != null ? update.parentSpanId().toString() : null);
        params.put("startTime", update.startTime() != null ? update.startTime().toString() : Instant.now().toString());
        params.put("workspaceId", update.workspaceId());
        params.put("createdBy", update.createdBy());
        return params;
    }

    /**
     * Helper method to map Neo4j record to Span object.
     */
    private Span mapToSpan(org.neo4j.driver.Record record) {
        var node = record.get("s").asNode();
        
        return Span.builder()
                .id(UUID.fromString(node.get("id").asString()))
                .projectId(UUID.fromString(node.get("projectId").asString()))
                .workspaceId(node.get("workspaceId").asString())
                .traceId(UUID.fromString(node.get("traceId").asString()))
                .parentSpanId(node.get("parentSpanId").asString() != null ? 
                        UUID.fromString(node.get("parentSpanId").asString()) : null)
                .name(node.get("name").asString())
                .type(node.get("type").asString() != null ? 
                        com.comet.opik.api.SpanType.valueOf(node.get("type").asString()) : null)
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
                .model(node.get("model").asString(null))
                .provider(node.get("provider").asString(null))
                .tags(node.get("tags").asList(Object::toString))
                .lastUpdatedAt(node.get("lastUpdatedAt").asString() != null ? 
                        Instant.parse(node.get("lastUpdatedAt").asString()) : null)
                .createdBy(node.get("createdBy").asString())
                .lastUpdatedBy(node.get("lastUpdatedBy").asString())
                .build();
    }
}

