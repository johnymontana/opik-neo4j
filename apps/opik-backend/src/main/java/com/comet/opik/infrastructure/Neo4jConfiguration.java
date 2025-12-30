package com.comet.opik.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for Neo4j database connection.
 * Maps to the neo4jDatabase section in config.yml.
 */
@Data
@NoArgsConstructor
public class Neo4jConfiguration {

    @NotEmpty
    @JsonProperty
    private String uri = "bolt://localhost:7687";

    @NotEmpty
    @JsonProperty
    private String username = "neo4j";

    @NotEmpty
    @JsonProperty
    private String password = "password";

    @NotEmpty
    @JsonProperty
    private String database = "neo4j";

    @NotNull
    @JsonProperty
    private Integer maxConnectionPoolSize = 50;

    @NotEmpty
    @JsonProperty
    private String connectionAcquisitionTimeout = "60s";

    @NotEmpty
    @JsonProperty
    private String maxConnectionLifetime = "3600s";

    @JsonProperty
    private boolean encrypted = false;

    @JsonProperty
    private String trustStrategy = "TRUST_ALL_CERTIFICATES";
}

