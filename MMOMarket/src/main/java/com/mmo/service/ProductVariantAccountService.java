package com.mmo.service;

import com.mmo.entity.ProductVariant;
import com.mmo.entity.ProductVariantAccount;
import com.mmo.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ProductVariantAccountService {

    @PersistenceContext
    private EntityManager entityManager;

    // Generate a .csv template with headers: Account|Seri, Password|PIN
    public byte[] buildTemplateCsv() {
        String csv = "Account|Seri,Password|PIN\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    // Preview upload: parse file, mark duplicates/invalid, return summary rows (limited)
    public PreviewResult previewUpload(User user, ProductVariant variant, MultipartFile file, boolean dedupe) throws IOException {
        List<AccountRow> normalized = parseAndNormalize(file);
        markDuplicatesByUsername(normalized, variant.getId());
        int invalidCount = 0;
        for (AccountRow r : normalized) if (r.invalid) invalidCount++;
        int max = Math.min(300, normalized.size());
        List<PreviewRow> rows = new ArrayList<>(max);
        int duplicateCount = 0;
        for (int i = 0; i < max; i++) {
            AccountRow r = normalized.get(i);
            if (r.duplicate) duplicateCount++;
            rows.add(new PreviewRow(nullToEmpty(r.username), nullToEmpty(r.password), r.duplicate, r.invalid, r.originalIndex));
        }
        if (normalized.size() > max) {
            for (int i = max; i < normalized.size(); i++) if (normalized.get(i).duplicate) duplicateCount++;
        }
        return new PreviewResult(normalized.size(), duplicateCount, invalidCount, rows);
    }

    // Confirm upload: persist ProductVariantAccount entries (skip duplicates if dedupe). Block if invalid rows exist.
    public UploadResult confirmUpload(User user, ProductVariant variant, MultipartFile file, boolean dedupe) throws IOException {
        List<AccountRow> normalized = parseAndNormalize(file);
        markDuplicatesByUsername(normalized, variant.getId());
        // Validate: both username and password are required per row
        long invalidCount = normalized.stream().filter(r -> r.invalid).count();
        if (invalidCount > 0) {
            throw new IllegalArgumentException("File has " + invalidCount + " invalid row(s) where Account or Password is missing. Please fix and try again.");
        }
        // Load existing usernames for the variant
        Set<String> existingUsernames = loadExistingUsernames(variant.getId());
        int created = 0;
        int skipped = 0;
        for (AccountRow r : normalized) {
            String uname = usernameKey(r);
            boolean isDup = r.duplicate || existingUsernames.contains(uname);
            if (dedupe && isDup) { skipped++; continue; }
            ProductVariantAccount acc = new ProductVariantAccount();
            acc.setVariant(variant);
            acc.setAccountData(buildAccountData(r)); // store as username:password
            acc.setStatus("Available");
            acc.setDelete(false);
            acc.setCreatedBy(user.getId());
            entityManager.persist(acc);
            created++;
            existingUsernames.add(uname); // avoid re-creating same username again in same batch
        }
        return new UploadResult(created, skipped);
    }

    // ===== Internal helpers =====
    private List<AccountRow> parseAndNormalize(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IOException("No file uploaded");
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".csv")) throw new IOException("Only .csv is accepted. Please use the provided CSV template.");
        List<AccountRow> rows = parseCsv(file);
        List<AccountRow> normalized = new ArrayList<>();
        for (AccountRow r : rows) {
            if (r == null) continue;
            String u = safeTrim(r.username);
            String p = safeTrim(r.password);
            if ((u == null || u.isEmpty()) && (p == null || p.isEmpty())) continue; // ignore fully empty
            AccountRow nr = new AccountRow(u, p, r.originalIndex);
            // mark invalid if exactly one is missing
            nr.invalid = (u == null || u.isEmpty()) ^ (p == null || p.isEmpty());
            normalized.add(nr);
        }
        // mark duplicates within file itself (by username)
        Set<String> seenUsernames = new HashSet<>();
        for (AccountRow r : normalized) {
            String uname = usernameKey(r);
            if (uname.isEmpty()) continue; // invalid or empty, already flagged above
            if (!seenUsernames.add(uname)) r.duplicate = true;
        }
        return normalized;
    }

    private void markDuplicatesByUsername(List<AccountRow> rows, Long variantId) {
        Set<String> existingUsernames = loadExistingUsernames(variantId);
        for (AccountRow r : rows) {
            String uname = usernameKey(r);
            if (!uname.isEmpty() && existingUsernames.contains(uname)) r.duplicate = true;
        }
    }

    private Set<String> loadExistingUsernames(Long variantId) {
        try {
            List<String> list = entityManager.createQuery(
                    "select a.accountData from ProductVariantAccount a where a.variant.id = :vid and a.isDelete = false",
                    String.class).setParameter("vid", variantId).getResultList();
            Set<String> usernames = new HashSet<>();
            if (list != null) {
                for (String s : list) {
                    if (s == null) continue;
                    int idx = s.indexOf(':');
                    String u = idx >= 0 ? s.substring(0, idx) : s;
                    if (u != null) usernames.add(u.trim());
                }
            }
            return usernames;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private List<AccountRow> parseCsv(MultipartFile file) throws IOException {
        List<AccountRow> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine(); if (header == null) return out;
            String[] cols = splitCsv(header);
            int uIdx = -1; int pIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                String c = normalizeHeader(cols[i]);
                if (uIdx < 0 && isUsernameHeader(c)) uIdx = i;
                if (pIdx < 0 && isPasswordHeader(c)) pIdx = i;
            }
            if (uIdx < 0 || pIdx < 0) return out;
            String line; int rowIndex = 1; // data row index starting from 1 after header
            while ((line = br.readLine()) != null) {
                String[] arr = splitCsv(line);
                String u = uIdx < arr.length ? arr[uIdx] : "";
                String p = pIdx < arr.length ? arr[pIdx] : "";
                out.add(new AccountRow(u, p, rowIndex));
                rowIndex++;
            }
        }
        return out;
    }

    private String[] splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { inQ = !inQ; continue; }
            if (ch == ',' && !inQ) { cols.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        cols.add(cur.toString());
        return cols.toArray(new String[0]);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String safeTrim(String s) { return s == null ? null : s.trim(); }
    private static String usernameKey(AccountRow r){ return r.username==null? "" : r.username.trim(); }
    private static String buildAccountData(AccountRow r){ String u = r.username==null? "" : r.username.trim(); String p = r.password==null? "" : r.password.trim(); return u + ":" + p; }

    // Header normalization and matching (accept Account|Seri and Password|PIN)
    private String normalizeHeader(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) sb.append(ch);
        }
        return sb.toString();
    }
    private boolean isUsernameHeader(String key) {
        return key.equals("username") || key.equals("user") || key.equals("account") || key.equals("accounts") || key.equals("acc") || key.equals("seri") || key.equals("accountseri");
    }
    private boolean isPasswordHeader(String key) {
        return key.equals("password") || key.equals("pass") || key.equals("pwd") || key.equals("pin") || key.equals("passwordpin");
    }

    // Data holders
    private static class AccountRow { String username; String password; boolean duplicate; boolean invalid; int originalIndex; AccountRow(String u, String p, int idx){ this.username=u; this.password=p; this.duplicate=false; this.invalid=false; this.originalIndex = idx; } }

    public static class PreviewRow { private final String username; private final String password; private final boolean duplicate; private final boolean invalid; private final int rowIndex; public PreviewRow(String u,String p, boolean d, boolean inv, int rowIdx){this.username=u;this.password=p;this.duplicate=d;this.invalid=inv;this.rowIndex=rowIdx;} public String getUsername(){return username;} public String getPassword(){return password;} public boolean isDuplicate(){return duplicate;} public boolean isInvalid(){return invalid;} public int getRowIndex(){return rowIndex;} }
    public static class PreviewResult { private final int count; private final int duplicateCount; private final int invalidCount; private final List<PreviewRow> rows; public PreviewResult(int c,int d,int inv,List<PreviewRow> r){this.count=c;this.duplicateCount=d;this.invalidCount=inv;this.rows=r;} public int getCount(){return count;} public int getDuplicateCount(){return duplicateCount;} public int getInvalidCount(){return invalidCount;} public List<PreviewRow> getRows(){return rows;} }
    public static class UploadResult { private final int created; private final int skipped; public UploadResult(int c,int s){this.created=c;this.skipped=s;} public int getCreated(){return created;} public int getSkipped(){return skipped;} }
}
