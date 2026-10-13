package com.retailerp.sales;

import com.retailerp.common.Party;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends Party {
}
