package org.clockworx.cotr.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for account membership records.
 * Maps to the {@code account_memberships} table with a composite primary key
 * of account name and player UUID.
 */
@Entity
@Table(name = "account_memberships")
@IdClass(AccountMembershipEntity.PK.class)
public class AccountMembershipEntity {

    @Id
    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Id
    @Column(name = "player_uuid", nullable = false)
    private String playerUuid;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected AccountMembershipEntity() {
    }

    public AccountMembershipEntity(String accountName, String playerUuid, String role, long createdAt) {
        this.accountName = accountName;
        this.playerUuid = playerUuid;
        this.role = role;
        this.createdAt = createdAt;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Composite primary key for {@link AccountMembershipEntity}.
     */
    public static class PK implements Serializable {
        private static final long serialVersionUID = 1L;

        private String accountName;
        private String playerUuid;

        public PK() {
        }

        public PK(String accountName, String playerUuid) {
            this.accountName = accountName;
            this.playerUuid = playerUuid;
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
                    && Objects.equals(playerUuid, pk.playerUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountName, playerUuid);
        }
    }
}
