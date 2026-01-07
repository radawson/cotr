package org.clockworx.cotr.bank;

/**
 * AccountRole - Enum representing the role a player has in an account
 * 
 * This enum defines the different permission levels for account access:
 * - OWNER: Full control, can delete account, manage members
 * - MEMBER: Can deposit, withdraw, view balance, but cannot manage members or delete account
 * - USER: Can deposit and withdraw with daily limits, cannot manage members or delete account
 * - CONTRIBUTOR: Can only deposit (unlimited), perfect for guild dues or donation accounts
 * - VIEWER: Can only view balance (reserved for future use)
 * 
 * Roles are used in the AccountMembership system to control access to
 * bank accounts in the many-to-many relationship between players and accounts.
 */
public enum AccountRole {
    /**
     * Owner role - has full control over the account.
     * Can:
     * - Deposit and withdraw funds
     * - View balance
     * - Add and remove members
     * - Delete the account
     * - Transfer funds
     */
    OWNER,
    
    /**
     * Member role - can use the account but not manage it.
     * Can:
     * - Deposit and withdraw funds
     * - View balance
     * - Transfer funds
     * Cannot:
     * - Add or remove members
     * - Delete the account
     */
    MEMBER,
    
    /**
     * User role - can deposit and withdraw with daily limits.
     * Can:
     * - Deposit funds (subject to daily limits)
     * - Withdraw funds (subject to daily limits)
     * - View balance
     * Cannot:
     * - Manage members
     * - Delete the account
     * 
     * Perfect for accounts where you want to limit transaction amounts per day.
     */
    USER,
    
    /**
     * Contributor role - can only deposit funds (unlimited).
     * Can:
     * - Deposit funds (unlimited)
     * - View balance
     * Cannot:
     * - Withdraw funds
     * - Manage members
     * - Delete the account
     * 
     * Perfect for guild dues, donation accounts, kingdom contributions, or any
     * scenario where you want players to contribute but not withdraw.
     */
    CONTRIBUTOR,
    
    /**
     * Viewer role - read-only access (reserved for future use).
     * Can:
     * - View balance
     * Cannot:
     * - Deposit or withdraw
     * - Manage members
     * - Delete account
     */
    VIEWER;
    
    /**
     * Checks if this role can perform administrative actions.
     * 
     * @return true if the role is OWNER, false otherwise
     */
    public boolean canManage() {
        return this == OWNER;
    }
    
    /**
     * Checks if this role can deposit or withdraw funds.
     * 
     * @return true if the role is OWNER, MEMBER, or USER, false otherwise
     */
    public boolean canTransact() {
        return this == OWNER || this == MEMBER || this == USER;
    }
    
    /**
     * Checks if this role can deposit funds.
     * 
     * @return true if the role is OWNER, MEMBER, USER, or CONTRIBUTOR, false otherwise
     */
    public boolean canDeposit() {
        return this == OWNER || this == MEMBER || this == USER || this == CONTRIBUTOR;
    }
    
    /**
     * Checks if this role can withdraw funds.
     * 
     * @return true if the role is OWNER, MEMBER, or USER, false otherwise
     */
    public boolean canWithdraw() {
        return this == OWNER || this == MEMBER || this == USER;
    }
    
    /**
     * Checks if this role can view the account balance.
     * 
     * @return true for all roles
     */
    public boolean canView() {
        return true;
    }
}
