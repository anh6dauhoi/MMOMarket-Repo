package com.mmo.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public enum Bank {
    VIETINBANK("VietinBank", "VIETINBANK|CTG", "970415"),
    VIETCOMBANK("Vietcombank", "VIETCOMBANK|VCB", "970436"),
    BIDV("BIDV", "BIDV", "970418"),
    AGRIBANK("Agribank", "AGRIBANK|AGRI", "970405"),
    OCB("OCB", "OCB", "970448"),
    MBBANK("MBBank", "MB BANK|MB", "970422"),
    TECHCOMBANK("Techcombank", "TECHCOMBANK|TCB", "970407"),
    ACB("ACB", "ACB", "970416"),
    VPBANK("VPBank", "VPBANK", "970432"),
    TPBANK("TPBank", "TPBANK", "970423"),
    SACOMBANK("Sacombank", "SACOMBANK", "970403"),
    HDBANK("HDBank", "HDBANK", "970437"),
    VIETCAPITALBANK("VietCapitalBank", "VIETCAPITALBANK", "970454"),
    SCB("SCB", "SCB", "970429"),
    VIB("VIB", "VIB", "970441"),
    SHB("SHB", "SHB", "970443"),
    EXIMBANK("Eximbank", "EXIMBANK", "970431"),
    MSB("MSB", "MSB", "970426"),
    CAKE("CAKE", "CAKE", "546034"),
    UBANK("Ubank", "UBANK", "546035"),

    // wallets / e-money / alternative channels
    VIETTELMONEY("ViettelMoney", "VIETTELMONEY", "971005"),
    TIMO("Timo", "TIMO", "963388"),
    VNPTMONEY("VNPTMoney", "VNPTMONEY", "970111"),
    SAIGONBANK("SaigonBank", "SAIGONBANK", "970400"),
    BACABANK("BacABank", "BACABANK", "970439"),
    PVCOMBANK_PAY("PVcomBank Pay", "PVCOMBANK|PVCOMBANK PAY", "970412"),
    NCB("NCB", "NCB", "970412"),
    MVB("MVB", "MVB", "970414"),
    SHINHANBANK("ShinhanBank", "SHINHANBANK", "970245"),
    ABBANK("ABBANK", "ABBANK", "970425"),
    VIETABANK("VietABank", "VIETABANK", "970427"),
    NAMABANK("NamABank", "NAMABANK", "970428"),
    PGBANK("PGBank", "PGBANK", "970430"),
    VIETBANK("VietBank", "VIETBANK", "970433"),
    BAOVIETBANK("BaoVietBank", "BAOVIETBANK", "970434"),
    COOPBANK("COOPBANK", "COOPBANK", "970448"),
    LPBANK("LPBank", "LPBANK", "970449"),

    // banks / organizations / financial institutions
    KIENLONGBANK("KienLongBank", "KIENLONGBANK", "970452"),
    KBANK("KBank", "KBANK", "668888"),
    MAFC("MAFC", "MAFC", "977777"),
    KEBHANAHN("KEBHANAHN", "KEBHANAHN", "970467"),
    KEBHANAHCM("KEBHanaHCM", "KEBHANAHCM|KEBHANAHCM", "970466"),
    CITIBANK("Citibank", "CITIBANK", "533948"),
    VBSP("VBSP", "VBSP", "999888"),
    CBBANK("CBBank", "CBBANK", "970444"),
    CIMB("CIMB", "CIMB", "422589"),
    DDSBANK("DDSBank", "DDSBANK", "796500"),
    VIKKI("Vikki", "VIKKI", "970406"),
    PUBLICBANK("PublicBank", "PUBLICBANK", "970439"),
    KOOKMIN_HCM("KookminHCM", "KOOKMINHCM", "970463"),
    KOOKMIN_HN("KookminHN", "KOOKMINHN", "970462"),
    WOORI("Woori", "WOORI", "970457"),
    VRB("VRB", "VRB", "970421"),
    GPBANK("GPBank", "GPBANK", "970408"),
    HONGLEONG("HongLeong", "HONGL EONG|HONGL EONG", "970442"); // kept name, code as provided

    private final String displayName;
    private final String aliasSpec; // pipe-separated aliases in uppercase
    private final String vietQrCode;

    Bank(String displayName, String aliasSpec, String vietQrCode) {
        this.displayName = displayName;
        this.aliasSpec = aliasSpec == null ? "" : aliasSpec.toUpperCase();
        this.vietQrCode = vietQrCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAliasSpec() {
        return aliasSpec;
    }

    public String getVietQrCode() {
        return vietQrCode;
    }

    public static List<Bank> listAll() {
        List<Bank> l = new ArrayList<>();
        Collections.addAll(l, Bank.values());
        return l;
    }

    // Find code by bank name (case-insensitive contains) using aliases sorted by length desc to prefer specific matches
    public static String findCodeForBankName(String bankName) {
        if (bankName == null) return null;
        String ub = bankName.toUpperCase();

        // Build list of (Bank, alias) pairs
        List<AliasEntry> entries = new ArrayList<>();
        for (Bank b : Bank.values()) {
            // split aliasSpec by '|' to allow multiple aliases per bank
            String[] aliases = b.aliasSpec.split("\\|");
            for (String a : aliases) {
                String as = a == null ? "" : a.trim();
                if (!as.isEmpty()) entries.add(new AliasEntry(b, as));
            }
            // also include displayName as alias for matching
            String dn = b.displayName == null ? "" : b.displayName.toUpperCase();
            if (!dn.isBlank()) entries.add(new AliasEntry(b, dn));
        }

        // Sort aliases by length desc so longer/more specific aliases are matched first
        entries.sort(Comparator.comparingInt((AliasEntry e) -> e.alias.length()).reversed());

        for (AliasEntry e : entries) {
            if (ub.contains(e.alias)) return e.bank.vietQrCode;
        }
        return null;
    }

    private static class AliasEntry {
        final Bank bank;
        final String alias;

        AliasEntry(Bank bank, String alias) {
            this.bank = bank;
            this.alias = alias == null ? "" : alias;
        }
    }
}
