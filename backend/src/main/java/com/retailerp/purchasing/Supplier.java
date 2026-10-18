package com.retailerp.purchasing;

import com.retailerp.common.Party;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier extends Party {
}
