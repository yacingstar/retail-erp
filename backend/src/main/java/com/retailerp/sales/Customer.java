package com.retailerp.sales;

import java.util.UUID;

import com.retailerp.common.Party;
import com.retailerp.pricing.PriceList;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends Party {

    /** The customer's group: their sales use this list's prices (when the plan includes Promotions). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_list_id")
    private PriceList priceList;

    void assignPriceList(PriceList priceList) {
        this.priceList = priceList;
    }

    @Override
    public UUID priceListId() {
        return priceList == null ? null : priceList.getId();
    }
}
