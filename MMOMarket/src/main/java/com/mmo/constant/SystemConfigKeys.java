package com.mmo.constant;

/**
 * Centralized keys for SystemConfiguration to avoid magic strings.
 */
public final class SystemConfigKeys {
    private SystemConfigKeys() {}

    // Commission
    public static final String COMMISSION_DEFAULT_PERCENTAGE = "commission.default_percentage";

    // System email
    public static final String SYSTEM_EMAIL_CONTACT = "system.email.contact";

    // Bank info used for top-up
    public static final String SYSTEM_BANK_NAME = "system.bank.name";
    public static final String SYSTEM_BANK_ACCOUNT_NUMBER = "system.bank.account_number";
    public static final String SYSTEM_BANK_ACCOUNT_NAME = "system.bank.account_name";

    // Policy URLs
    public static final String POLICY_COMPLAINT_URL = "system_complaint";
    public static final String POLICY_SELLER_AGREEMENT_URL = "system_contract";
}
