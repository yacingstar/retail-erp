package com.retailerp.common;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Shared fields of suppliers and customers (both are "parties" we do business with). */
@MappedSuperclass
public abstract class Party extends AuditedEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 320)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public void update(PartyDtos.PartyRequest request) {
        this.name = request.name().trim();
        this.email = request.email();
        this.phone = request.phone();
        this.address = request.address();
        this.notes = request.notes();
        this.active = request.active() == null || request.active();
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    /** Only customers have a price list; suppliers return null. */
    public UUID priceListId() {
        return null;
    }
}
