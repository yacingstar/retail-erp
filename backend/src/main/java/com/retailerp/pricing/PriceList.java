package com.retailerp.pricing;

import java.util.ArrayList;
import java.util.List;

import com.retailerp.common.AuditedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Special prices for a group of customers (e.g. "Wholesale"). Products not on the list keep their normal price. */
@Entity
@Table(name = "price_lists")
public class PriceList extends AuditedEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "priceList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceListItem> items = new ArrayList<>();

    protected PriceList() {
    }

    PriceList(String name) {
        this.name = name;
    }

    void update(String name, boolean active, List<PriceListItem> newItems) {
        this.name = name;
        this.active = active;
        items.clear();
        for (PriceListItem item : newItems) {
            item.attach(this);
            items.add(item);
        }
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public List<PriceListItem> getItems() {
        return items;
    }
}
