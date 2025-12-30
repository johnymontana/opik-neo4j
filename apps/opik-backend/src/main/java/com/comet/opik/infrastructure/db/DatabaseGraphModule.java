package com.comet.opik.infrastructure.db;

import com.comet.opik.infrastructure.Neo4jConfiguration;
import com.comet.opik.infrastructure.OpikConfiguration;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Guice module for Neo4j database configuration.
 * Provides Neo4j Driver instance for all database operations.
 */
@Slf4j
public class DatabaseGraphModule extends DropwizardAwareModule<OpikConfiguration> {

    private transient Driver driver;
    private transient Neo4jConfiguration neo4jConfiguration;

    @Override
    protected void configure() {
        neo4jConfiguration = configuration().getNeo4jDatabase();
        
        log.info("Initializing Neo4j driver with URI: '{}'", neo4jConfiguration.getUri());
        
        Config.ConfigBuilder configBuilder = Config.builder()
                .withMaxConnectionPoolSize(neo4jConfiguration.getMaxConnectionPoolSize())
                .withConnectionAcquisitionTimeout(
                        parseDuration(neo4jConfiguration.getConnectionAcquisitionTimeout()), 
                        TimeUnit.SECONDS
                )
                .withMaxConnectionLifetime(
                        parseDuration(neo4jConfiguration.getMaxConnectionLifetime()), 
                        TimeUnit.SECONDS
                );

        // Configure encryption if enabled
        if (neo4jConfiguration.isEncrypted()) {
            configBuilder.withEncryption();
            
            if ("TRUST_ALL_CERTIFICATES".equals(neo4jConfiguration.getTrustStrategy())) {
                configBuilder.withTrustStrategy(Config.TrustStrategy.trustAllCertificates());
            } else {
                configBuilder.withTrustStrategy(Config.TrustStrategy.trustSystemCertificates());
            }
        } else {
            configBuilder.withoutEncryption();
        }

        driver = GraphDatabase.driver(
                neo4jConfiguration.getUri(),
                AuthTokens.basic(neo4jConfiguration.getUsername(), neo4jConfiguration.getPassword()),
                configBuilder.build()
        );

        log.info("Neo4j driver initialized successfully");
    }

    @Provides
    @Singleton
    public Driver getDriver() {
        return driver;
    }

    @Provides
    @Singleton
    public String getDatabaseName() {
        return neo4jConfiguration.getDatabase();
    }

    /**
     * Parse duration string (e.g., "60s", "1m", "1h") to seconds.
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 60; // Default to 60 seconds
        }
        
        duration = duration.trim().toLowerCase();
        long value;
        long multiplier = 1;
        
        if (duration.endsWith("ms")) {
            value = Long.parseLong(duration.substring(0, duration.length() - 2));
            multiplier = 1; // milliseconds
            return value / 1000; // Convert to seconds
        } else if (duration.endsWith("s")) {
            value = Long.parseLong(duration.substring(0, duration.length() - 1));
            multiplier = 1; // seconds
        } else if (duration.endsWith("m")) {
            value = Long.parseLong(duration.substring(0, duration.length() - 1));
            multiplier = 60; // minutes to seconds
        } else if (duration.endsWith("h")) {
            value = Long.parseLong(duration.substring(0, duration.length() - 1));
            multiplier = 3600; // hours to seconds
        } else {
            value = Long.parseLong(duration);
            multiplier = 1; // Assume seconds if no unit
        }
        
        return value * multiplier;
    }
}

