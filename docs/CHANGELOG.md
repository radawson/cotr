# Changelog

All notable changes to Coin of the Realm will be documented in this file.

## [0.5.0]

### Added
- **Enhanced Bank Account Roles**: Added two new account roles with granular permissions
  - **USER role**: Can deposit and withdraw with daily transaction limits (default: 1000 coins/day)
    - Perfect for accounts where you want to limit transaction amounts per day
    - Daily limits are tracked per player per account
  - **CONTRIBUTOR role**: Can only deposit funds (unlimited), cannot withdraw
    - Perfect for guild dues, donation accounts, kingdom contributions
    - Allows players to contribute to accounts without withdrawal access
- **Daily Transaction Limits System**: 
  - New `daily_transactions` database table tracks daily deposit/withdrawal totals
  - Automatic limit enforcement for USER role accounts
  - Configurable limits (currently defaults to 1000 coins/day, will be configurable in future versions)
- **Enhanced Permission Checks**: 
  - Deposit operations now check `canDeposit()` permission (prevents VIEWER from depositing)
  - Withdrawal operations now check `canWithdraw()` permission (prevents CONTRIBUTOR and VIEWER from withdrawing)
  - Proper role-based access control for all banking operations

### Changed
- **Account Role System**: Updated permission methods in `AccountRole` enum
  - Added `canDeposit()` method: Returns true for OWNER, MEMBER, USER, CONTRIBUTOR
  - Added `canWithdraw()` method: Returns true for OWNER, MEMBER, USER
  - Updated `canTransact()` to include USER role
- **Database Schema**: Updated to version 2
  - Added `daily_transactions` table for tracking daily limits
  - Automatic migration from schema version 1 to 2
- **Command Handler**: Updated `/cotr account add` command to accept USER and CONTRIBUTOR roles
  - Error messages now list all available roles: OWNER, MEMBER, USER, CONTRIBUTOR, VIEWER

### Fixed
- **Permission Enforcement**: Fixed issue where deposit/withdraw only checked `hasAccess()` instead of role-specific permissions
  - VIEWER role can no longer deposit or withdraw (was previously allowed)
  - CONTRIBUTOR role can no longer withdraw (now properly enforced)

