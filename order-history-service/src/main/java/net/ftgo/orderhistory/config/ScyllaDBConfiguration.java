package net.ftgo.orderhistory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

/**
 * ScyllaDB (Cassandra-compatible) configuration for Order History Service.
 * 
 * ScyllaDB provides:
 * - High-performance reads with low latency
 * - Horizontal scalability by adding nodes
 * - Cassandra Query Language (CQL) compatibility
 * - Materialized views for efficient query patterns
 * 
 * Configuration:
 * - Contact points: ScyllaDB cluster nodes
 * - Keyspace: ftgo_order_history
 * - Schema action: CREATE_IF_NOT_EXISTS (creates tables on startup)
 * - Local datacenter: datacenter1 (for multi-DC deployments)
 */
@Configuration
@EnableCassandraRepositories(basePackages = "net.ftgo.orderhistory.repository")
public class ScyllaDBConfiguration extends AbstractCassandraConfiguration {
    
    @Value("${spring.cassandra.keyspace-name}")
    private String keyspaceName;
    
    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;
    
    @Value("${spring.cassandra.port}")
    private int port;
    
    @Value("${spring.cassandra.local-datacenter}")
    private String localDatacenter;
    
    @Override
    protected String getKeyspaceName() {
        return keyspaceName;
    }
    
    @Override
    protected String getContactPoints() {
        return contactPoints;
    }
    
    @Override
    protected int getPort() {
        return port;
    }
    
    @Override
    protected String getLocalDataCenter() {
        return localDatacenter;
    }
    
    @Override
    public SchemaAction getSchemaAction() {
        return SchemaAction.CREATE_IF_NOT_EXISTS;
    }
    
    // Metrics are enabled by default in Spring Data Cassandra 3.x+
}
