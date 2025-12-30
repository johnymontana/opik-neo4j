package com.comet.opik.infrastructure.health;

import com.codahale.metrics.health.HealthCheck;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

/**
 * Health check for Neo4j database connectivity.
 * Verifies that the Neo4j database is accessible and responsive.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class Neo4jHealthCheck extends HealthCheck {

    private final Driver driver;
    private final String databaseName;

    @Override
    protected Result check() throws Exception {
        try {
            // Attempt to connect to Neo4j and run a simple query
            try (Session session = driver.session(SessionConfig.forDatabase(databaseName))) {
                var result = session.run("RETURN 1 AS result");
                
                if (result.hasNext()) {
                    var record = result.next();
                    if (record.get("result").asInt() == 1) {
                        log.debug("Neo4j health check passed");
                        return Result.healthy("Neo4j is up and running");
                    }
                }
                
                log.warn("Neo4j health check failed: unexpected query result");
                return Result.unhealthy("Neo4j query returned unexpected result");
            }
        } catch (Exception e) {
            log.error("Neo4j health check failed with exception", e);
            return Result.unhealthy("Neo4j is not accessible: " + e.getMessage());
        }
    }
}

