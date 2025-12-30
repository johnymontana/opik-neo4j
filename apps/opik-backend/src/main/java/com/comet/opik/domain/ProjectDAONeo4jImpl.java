package com.comet.opik.domain;

import com.comet.opik.api.Project;
import com.comet.opik.api.ProjectStats;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Neo4j implementation of ProjectDAO using Cypher queries.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProjectDAONeo4jImpl {

    private final Neo4jTransactionTemplate neo4jTemplate;

    public Mono<Project> save(@NonNull Project project) {
        log.info("Saving project with id '{}'", project.getId());
        
        String cypher = """
                MATCH (w:Workspace {id: $workspaceId})
                MERGE (p:Project {id: $id})
                ON CREATE SET
                    p.name = $name,
                    p.workspaceId = $workspaceId,
                    p.description = $description,
                    p.createdAt = datetime($createdAt),
                    p.createdBy = $createdBy,
                    p.lastUpdatedAt = datetime($lastUpdatedAt),
                    p.lastUpdatedBy = $lastUpdatedBy
                ON MATCH SET
                    p.name = $name,
                    p.description = $description,
                    p.lastUpdatedAt = datetime($lastUpdatedAt),
                    p.lastUpdatedBy = $lastUpdatedBy
                MERGE (w)-[:CONTAINS]->(p)
                RETURN p
                """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", project.getId().toString());
        params.put("name", project.getName());
        params.put("workspaceId", project.getWorkspaceId());
        params.put("description", project.getDescription());
        params.put("createdAt", project.getCreatedAt() != null ? project.getCreatedAt().toString() : Instant.now().toString());
        params.put("createdBy", project.getCreatedBy());
        params.put("lastUpdatedAt", project.getLastUpdatedAt() != null ? project.getLastUpdatedAt().toString() : Instant.now().toString());
        params.put("lastUpdatedBy", project.getLastUpdatedBy());
        
        return neo4jTemplate.executeWrite(cypher, params, record -> mapToProject(record));
    }

    public Mono<Project> findById(@NonNull UUID id) {
        log.info("Finding project by id '{}'", id);
        
        String cypher = "MATCH (p:Project {id: $id}) RETURN p";
        return neo4jTemplate.executeRead(cypher, Map.of("id", id.toString()), record -> mapToProject(record));
    }

    public Flux<Project> findByWorkspaceId(@NonNull String workspaceId) {
        log.info("Finding projects for workspace '{}'", workspaceId);
        
        String cypher = """
                MATCH (w:Workspace {id: $workspaceId})-[:CONTAINS]->(p:Project)
                RETURN p
                ORDER BY p.createdAt DESC
                """;
        
        return neo4jTemplate.executeReadFlux(cypher, Map.of("workspaceId", workspaceId), record -> mapToProject(record));
    }

    public Mono<Void> delete(@NonNull UUID id) {
        log.info("Deleting project with id '{}'", id);
        
        String cypher = """
                MATCH (p:Project {id: $id})
                DETACH DELETE p
                """;
        
        return neo4jTemplate.executeWriteVoid(cypher, Map.of("id", id.toString()));
    }

    public Mono<Long> count(@NonNull String workspaceId) {
        String cypher = """
                MATCH (w:Workspace {id: $workspaceId})-[:CONTAINS]->(p:Project)
                RETURN count(p) as count
                """;
        
        return neo4jTemplate.executeRead(cypher, Map.of("workspaceId", workspaceId), 
                record -> record.get("count").asLong());
    }

    private Project mapToProject(org.neo4j.driver.Record record) {
        var node = record.get("p").asNode();
        
        return Project.builder()
                .id(UUID.fromString(node.get("id").asString()))
                .name(node.get("name").asString())
                .workspaceId(node.get("workspaceId").asString())
                .description(node.get("description").asString(null))
                .createdAt(node.get("createdAt").asString() != null ? 
                        Instant.parse(node.get("createdAt").asString()) : null)
                .createdBy(node.get("createdBy").asString())
                .lastUpdatedAt(node.get("lastUpdatedAt").asString() != null ? 
                        Instant.parse(node.get("lastUpdatedAt").asString()) : null)
                .lastUpdatedBy(node.get("lastUpdatedBy").asString())
                .build();
    }
}

