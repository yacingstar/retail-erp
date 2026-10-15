package com.retailerp.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Wraps the connection pool so that every borrowed connection is tagged with the current tenant:
 * {@code SELECT set_config('app.tenant_id', ?, false)}.
 * <p>
 * The Postgres Row-Level Security policies compare each row's tenant_id with that setting, so even a query
 * that forgets the tenant filter (native SQL, a bug, a missing {@code @TenantId}) only sees the current
 * tenant's rows. The value is set on EVERY borrow, so a pooled connection never keeps a previous tenant.
 * <p>
 * It runs before Spring switches off auto-commit for a transaction, so the setting is committed immediately
 * and lasts for the whole time the connection is borrowed.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return tag(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return tag(super.getConnection(username, password));
    }

    private Connection tag(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.get();
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId != null ? tenantId.toString() : "");
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
