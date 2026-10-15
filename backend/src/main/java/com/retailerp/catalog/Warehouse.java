package com.retailerp.catalog;

import com.retailerp.common.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A place where stock is kept (a store, a back room, a central warehouse). */
@Entity
@Table(name = "warehouses")
public class Warehouse extends AuditedEntity {

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    protected Warehouse() {
    }

    public Warehouse(String code) {
        this.code = code;
    }

    public void update(String code, String name, String address, boolean active) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }
}
