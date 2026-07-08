package org.clockworx.cotr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for per-region emerald supply and demand tracking totals.
 * Maps to the {@code emerald_region_stats} table.
 */
@Entity
@Table(name = "emerald_region_stats")
public class EmeraldRegionStatsEntity {

    @Id
    @Column(name = "region_id", nullable = false)
    private String regionId;

    @Column(name = "mined_total", nullable = false)
    private int minedTotal;

    @Column(name = "loot_total", nullable = false)
    private int lootTotal;

    @Column(name = "mob_total", nullable = false)
    private int mobTotal;

    @Column(name = "trading_total", nullable = false)
    private int tradingTotal;

    @Column(name = "bank_in_total", nullable = false)
    private int bankInTotal;

    @Column(name = "bank_out_total", nullable = false)
    private int bankOutTotal;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected EmeraldRegionStatsEntity() {
    }

    public EmeraldRegionStatsEntity(String regionId) {
        this.regionId = regionId;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public int getMinedTotal() {
        return minedTotal;
    }

    public void setMinedTotal(int minedTotal) {
        this.minedTotal = minedTotal;
    }

    public int getLootTotal() {
        return lootTotal;
    }

    public void setLootTotal(int lootTotal) {
        this.lootTotal = lootTotal;
    }

    public int getMobTotal() {
        return mobTotal;
    }

    public void setMobTotal(int mobTotal) {
        this.mobTotal = mobTotal;
    }

    public int getTradingTotal() {
        return tradingTotal;
    }

    public void setTradingTotal(int tradingTotal) {
        this.tradingTotal = tradingTotal;
    }

    public int getBankInTotal() {
        return bankInTotal;
    }

    public void setBankInTotal(int bankInTotal) {
        this.bankInTotal = bankInTotal;
    }

    public int getBankOutTotal() {
        return bankOutTotal;
    }

    public void setBankOutTotal(int bankOutTotal) {
        this.bankOutTotal = bankOutTotal;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
