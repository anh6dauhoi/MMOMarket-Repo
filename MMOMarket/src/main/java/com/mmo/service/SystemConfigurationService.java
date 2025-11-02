package com.mmo.service;

import com.mmo.entity.SystemConfiguration;
import com.mmo.entity.User;
import com.mmo.repository.SystemConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.mmo.constant.SystemConfigKeys.*;

@Service
public class SystemConfigurationService {

    private final SystemConfigurationRepository repo;

    public SystemConfigurationService(SystemConfigurationRepository repo) {
        this.repo = repo;
    }

    public static class DefaultDef {
        public final String key;
        public final String defaultValue;
        public final String description;
        public final String valueType;
        public DefaultDef(String key, String defaultValue, String description, String valueType) {
            this.key = key; this.defaultValue = defaultValue; this.description = description; this.valueType = valueType;
        }
    }

    // Default configuration definitions (translated to English)
    public static final List<DefaultDef> DEFAULTS = List.of(
            new DefaultDef(COMMISSION_DEFAULT_PERCENTAGE, "5.00", "Default commission rate applied to new sellers or those without a level.", "decimal"),
            new DefaultDef(SYSTEM_EMAIL_CONTACT, "contact@mmomarket.xyz", "Email address for user support/contact.", "string"),
            new DefaultDef(SYSTEM_BANK_NAME, "MBBank", "Bank name of the system's receiving account.", "string"),
            new DefaultDef(SYSTEM_BANK_ACCOUNT_NUMBER, "0813302283", "Account number used to receive funds for the system.", "string"),
            new DefaultDef(SYSTEM_BANK_ACCOUNT_NAME, "Tran Tuan Anh", "Account holder name of the system's receiving account.", "string"),
            // Updated descriptions to Vietnamese per requirement
            new DefaultDef(POLICY_COMPLAINT_URL, "/policy/complaint-resolution", "Quy chế giải quyết khiếu nại.", "string"),
            new DefaultDef(POLICY_SELLER_AGREEMENT_URL, "/policy/seller-agreement", "Hợp đồng/thỏa thuận người bán.", "string")
    );

    public Map<String, SystemConfiguration> getAllAsMap() {
        Map<String, SystemConfiguration> map = new LinkedHashMap<>();
        for (SystemConfiguration sc : repo.findAll()) {
            map.put(sc.getConfigKey(), sc);
        }
        return map;
    }

    public List<SystemConfiguration> getAll() {
        return repo.findAll();
    }

    public Optional<SystemConfiguration> getByKey(String key) {
        return repo.findById(key);
    }

    // Helper getters for typed access
    public String getStringValue(String key, String fallback) {
        try {
            return repo.findById(key).map(SystemConfiguration::getConfigValue)
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    public BigDecimal getDecimalValue(String key, BigDecimal fallback) {
        try {
            String v = repo.findById(key).map(SystemConfiguration::getConfigValue).orElse(null);
            if (v == null || v.trim().isEmpty()) return fallback;
            BigDecimal bd = new BigDecimal(v.trim()).setScale(2, RoundingMode.HALF_UP);
            return bd;
        } catch (Exception e) {
            return fallback;
        }
    }

    public BigDecimal getDefaultCommissionPercentage() {
        return getDecimalValue(COMMISSION_DEFAULT_PERCENTAGE, new BigDecimal("5.00"));
    }

    @Transactional
    public void ensureDefaults() {
        Map<String, SystemConfiguration> current = getAllAsMap();
        for (DefaultDef def : DEFAULTS) {
            if (!current.containsKey(def.key)) {
                SystemConfiguration sc = new SystemConfiguration();
                sc.setConfigKey(def.key);
                sc.setConfigValue(def.defaultValue);
                sc.setDescription(def.description);
                sc.setValueType(def.valueType);
                repo.save(sc);
            } else {
                // ensure description/valueType are up to date if null
                SystemConfiguration sc = current.get(def.key);
                boolean changed = false;
                if (sc.getDescription() == null || sc.getDescription().isBlank()) { sc.setDescription(def.description); changed = true; }
                if (sc.getValueType() == null || sc.getValueType().isBlank()) { sc.setValueType(def.valueType); changed = true; }
                if (changed) repo.save(sc);
            }
        }
    }

    @Transactional
    public Map<String, String> updateConfigs(Map<String, String> updates, User updatedBy) {
        // returns a map of field -> error message for any validation errors; empty if ok
        Map<String, String> errors = new LinkedHashMap<>();
        // Load existing
        Map<String, SystemConfiguration> all = getAllAsMap();
        // Validate and apply
        for (DefaultDef def : DEFAULTS) {
            String key = def.key;
            if (!updates.containsKey(key)) continue; // ignore unknown keys in form
            String raw = updates.get(key) != null ? updates.get(key).trim() : "";

            if ("decimal".equalsIgnoreCase(def.valueType)) {
                if (raw.isEmpty()) {
                    errors.put(key, "Value must not be empty.");
                    continue;
                }
                try {
                    BigDecimal bd = new BigDecimal(raw);
                    // specific validation: commission percentage must be between 0 and 100
                    if (COMMISSION_DEFAULT_PERCENTAGE.equals(key)) {
                        if (bd.compareTo(BigDecimal.ZERO) < 0 || bd.compareTo(new BigDecimal("100")) > 0) {
                            errors.put(key, "Commission percentage must be between 0 and 100.");
                            continue;
                        }
                    }
                    bd = bd.setScale(2, RoundingMode.HALF_UP);
                    raw = bd.toPlainString();
                } catch (Exception ex) {
                    errors.put(key, "Value must be a valid number.");
                    continue;
                }
            } else {
                // string type: basic non-empty for some keys
                if (key.startsWith("system.bank.")) {
                    if (raw.isEmpty()) {
                        errors.put(key, "This field is required.");
                        continue;
                    }
                }
            }

            SystemConfiguration sc = all.getOrDefault(key, new SystemConfiguration());
            sc.setConfigKey(key);
            sc.setConfigValue(raw);
            sc.setDescription(def.description);
            sc.setValueType(def.valueType);
            if (updatedBy != null) sc.setUpdatedBy(updatedBy);
            repo.save(sc);
        }
        return errors;
    }
}
