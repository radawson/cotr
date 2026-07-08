package org.clockworx.cotr.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for per-player daily transaction limit totals.
 * Maps to the {@code daily_transactions} table with a composite primary key
 * of account name, player UUID, and date.
 */
@Entity
@Table(name = "daily_transactions")
@IdClass(DailyTransactionEntity.PK.class)
public class DailyTransactionEntity {

    @Id
    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Id
    @Column(name = "player_uuid", nullable = false)
    private String playerUuid;

    @Id
    @Column(name = "date", nullable = false)
    private String date;

    @Column(name = "deposit_total", nullable = false)
    private int depositTotal;

    @Column(name = "withdraw_total", nullable = false)
    private int withdrawTotal;

    protected DailyTransactionEntity() {
    }

    public DailyTransactionEntity(String accountName, String playerUuid, String date,
                                  int depositTotal, int withdrawTotal) {
        this.accountName = accountName;
        this.playerUuid = playerUuid;
        this.date = date;
        this.depositTotal = depositTotal;
        this.withdrawTotal = withdrawTotal;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getDepositTotal() {
        return depositTotal;
    }

    public void setDepositTotal(int depositTotal) {
        this.depositTotal = depositTotal;
    }

    public int getWithdrawTotal() {
        return withdrawTotal;
    }

    public void setWithdrawTotal(int withdrawTotal) {
        this.withdrawTotal = withdrawTotal;
    }

    /**
     * Composite primary key for {@link DailyTransactionEntity}.
     */
    public static class PK implements Serializable {
        private static final long serialVersionUID = 1L;

        private String accountName;
        private String playerUuid;
        private String date;

        public PK() {
        }

        public PK(String accountName, String playerUuid, String date) {
            this.accountName = accountName;
            this.playerUuid = playerUuid;
            this.date = date;
        }

        public String getAccountName() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        public String getPlayerUuid() {
            return playerUuid;
        }

        public void setPlayerUuid(String playerUuid) {
            this.playerUuid = playerUuid;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PK pk = (PK) o;
            return Objects.equals(accountName, pk.accountName)
                    && Objects.equals(playerUuid, pk.playerUuid)
                    && Objects.equals(date, pk.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountName, playerUuid, date);
        }
    }
}
