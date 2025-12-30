package com.comet.opik.infrastructure.db;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.reactive.ReactiveSession;
import org.neo4j.driver.reactive.ReactiveTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * Template for executing Neo4j queries within reactive transactions.
 * Provides methods for executing Cypher queries that return Mono or Flux results.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class Neo4jTransactionTemplate {

    private final Driver driver;
    private final String databaseName;

    /**
     * Execute a query in a write transaction and return a Mono result.
     * 
     * @param query The Cypher query to execute
     * @param parameters Query parameters
     * @param mapper Function to map the result to the desired type
     * @return Mono with the mapped result
     */
    public <T> Mono<T> executeWrite(String query, Map<String, Object> parameters, 
                                     Function<Record, T> mapper) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Mono.from(session.executeWrite(tx -> {
                    var result = tx.run(new Query(query, parameters));
                    return Mono.from(result.records())
                            .map(mapper)
                            .switchIfEmpty(Mono.empty());
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute a query in a write transaction without expecting a result.
     * 
     * @param query The Cypher query to execute
     * @param parameters Query parameters
     * @return Mono<Void> signaling completion
     */
    public Mono<Void> executeWriteVoid(String query, Map<String, Object> parameters) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Mono.from(session.executeWrite(tx -> {
                    var result = tx.run(new Query(query, parameters));
                    return Mono.from(result.consume()).then();
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute a query in a write transaction and return multiple results as Flux.
     * 
     * @param query The Cypher query to execute
     * @param parameters Query parameters
     * @param mapper Function to map each result to the desired type
     * @return Flux with the mapped results
     */
    public <T> Flux<T> executeWriteFlux(String query, Map<String, Object> parameters, 
                                         Function<Record, T> mapper) {
        return Flux.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Flux.from(session.executeWrite(tx -> {
                    var result = tx.run(new Query(query, parameters));
                    return Flux.from(result.records()).map(mapper);
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute a query in a read transaction and return a Mono result.
     * 
     * @param query The Cypher query to execute
     * @param parameters Query parameters
     * @param mapper Function to map the result to the desired type
     * @return Mono with the mapped result
     */
    public <T> Mono<T> executeRead(String query, Map<String, Object> parameters, 
                                    Function<Record, T> mapper) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Mono.from(session.executeRead(tx -> {
                    var result = tx.run(new Query(query, parameters));
                    return Mono.from(result.records())
                            .map(mapper)
                            .switchIfEmpty(Mono.empty());
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute a query in a read transaction and return multiple results as Flux.
     * 
     * @param query The Cypher query to execute
     * @param parameters Query parameters
     * @param mapper Function to map each result to the desired type
     * @return Flux with the mapped results
     */
    public <T> Flux<T> executeReadFlux(String query, Map<String, Object> parameters, 
                                        Function<Record, T> mapper) {
        return Flux.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Flux.from(session.executeRead(tx -> {
                    var result = tx.run(new Query(query, parameters));
                    return Flux.from(result.records()).map(mapper);
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute multiple queries in a single write transaction.
     * 
     * @param transactionFunction Function that receives a transaction and performs operations
     * @return Mono signaling completion
     */
    public <T> Mono<T> executeInTransaction(Function<ReactiveTransaction, Mono<T>> transactionFunction) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Mono.from(session.executeWrite(transactionFunction::apply)),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Execute a batch write operation (multiple queries in one transaction).
     * 
     * @param queries List of Cypher queries with their parameters
     * @return Mono<Void> signaling completion
     */
    public Mono<Void> executeBatchWrite(java.util.List<QueryWithParams> queries) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class, 
                        SessionConfig.forDatabase(databaseName))),
                session -> Mono.from(session.executeWrite(tx -> {
                    return Flux.fromIterable(queries)
                            .flatMap(qp -> {
                                var result = tx.run(new Query(qp.query(), qp.parameters()));
                                return Mono.from(result.consume());
                            })
                            .then();
                })),
                session -> Mono.fromDirect(session.close())
        );
    }

    /**
     * Helper record to hold query and parameters for batch operations.
     */
    public record QueryWithParams(String query, Map<String, Object> parameters) {}
}

