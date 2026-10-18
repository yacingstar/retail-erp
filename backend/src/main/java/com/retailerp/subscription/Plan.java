package com.retailerp.subscription;

import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * A plan from the global catalogue (not tenant-owned, so no {@code @TenantId}). Read-only for the app:
 * plans change through Flyway migrations.
 * <p>
 * {@code @ElementCollection}: a set of simple values (not entities) stored in its own table, plan_modules.
 */
@Entity
@Immutable
@Table(name = "plans")
public class Plan {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** null = unlimited. */
    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_warehouses")
    private Integer maxWarehouses;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_modules", joinColumns = @JoinColumn(name = "plan_code"))
    @Column(name = "module", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Module> modules = EnumSet.noneOf(Module.class);

    protected Plan() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public Integer getMaxWarehouses() {
        return maxWarehouses;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Set<Module> getModules() {
        return modules;
    }
}
