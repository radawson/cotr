package org.clockworx.cotr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for stored emerald-to-coin exchange rates per region.
 * Maps to the {@code emerald_exchange_rates} table.
 */
@Entity
@Table(name = "emerald_exchange_rates")
public class EmeraldExchangeRateEntity {

    @Id
    @Column(name = "region_id", nullable = false)
    private String regionId;

    @Column(name = "current_rate", nullable = false)
    private int currentRate;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected EmeraldExchangeRateEntity() {
    }

    public EmeraldExchangeRateEntity(String regionId, int currentRate, long updatedAt) {
        this.regionId = regionId;
        this.currentRate = currentRate;
        this.updatedAt = updatedAt;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public int getCurrentRate() {
        return currentRate;
    }

    public void setCurrentRate(int currentRate) {
        this.currentRate = currentRate;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
