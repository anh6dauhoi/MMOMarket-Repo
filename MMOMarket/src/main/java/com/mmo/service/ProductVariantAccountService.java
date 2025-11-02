package com.mmo.service;

import com.mmo.entity.ProductVariantAccount;
import com.mmo.repository.ProductVariantAccountRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ProductVariantAccountService {

    @Autowired
    private ProductVariantAccountRepository accountRepository;

    /**
     * Import accounts từ file Excel
     * File Excel format: Cột 0 = Username, Cột 1 = Password
     */
    @Transactional
    public int importAccountsFromExcel(MultipartFile file, Long variantId, Long userId) throws IOException {
        List<ProductVariantAccount> accounts = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                // Bỏ qua dòng header (row 0) và dòng trống
                if (row == null || row.getRowNum() == 0) continue;

                Cell usernameCell = row.getCell(0);
                Cell passwordCell = row.getCell(1);

                if (usernameCell == null || passwordCell == null) continue;

                String username = getCellValueAsString(usernameCell);
                String password = getCellValueAsString(passwordCell);

                if (username.isEmpty() || password.isEmpty()) continue;

                // Tạo account mới
                ProductVariantAccount account = new ProductVariantAccount();
                account.setVariantId(variantId);
                account.setAccountData(username + "|" + password); // Format: username|password
                account.setStatus(ProductVariantAccount.AccountStatus.Available);
                account.setActivated(false);
                account.setDelete(false);
                account.setCreatedAt(new Date());
                account.setCreatedBy(userId);

                accounts.add(account);
            }
        }

        // Lưu tất cả accounts
        if (!accounts.isEmpty()) {
            accountRepository.saveAll(accounts);
        }

        return accounts.size();
    }

    /**
     * Helper method để đọc cell value từ Excel
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // Convert số thành string (để tránh format như 1.23E+10)
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Đọc giá trị đã tính của formula
                return cell.getStringCellValue().trim();
            default:
                return "";
        }
    }

    /**
     * Đếm số lượng account Available của một variant
     */
    public long countAvailableAccounts(Long variantId) {
        return accountRepository.countByVariantIdAndStatusAndIsDeleteFalse(
                variantId,
                ProductVariantAccount.AccountStatus.Available
        );
    }

    /**
     * Lấy danh sách account Available (giới hạn số lượng)
     * Dùng khi customer mua hàng
     */
    public List<ProductVariantAccount> getAvailableAccounts(Long variantId, int quantity) {
        List<ProductVariantAccount> allAvailable = accountRepository
                .findByVariantIdAndStatusAndIsDeleteFalseOrderByCreatedAtAsc(
                        variantId,
                        ProductVariantAccount.AccountStatus.Available
                );

        // Lấy số lượng cần thiết
        return allAvailable.subList(0, Math.min(quantity, allAvailable.size()));
    }

    /**
     * Lấy tất cả account của một variant (để hiển thị cho seller)
     */
    public List<ProductVariantAccount> getAccountsByVariantId(Long variantId) {
        return accountRepository.findByVariantIdAndIsDeleteFalse(variantId);
    }

    /**
     * Lấy account đã bán theo transaction
     */
    public List<ProductVariantAccount> getAccountsByTransactionId(Long transactionId) {
        return accountRepository.findByTransactionIdAndIsDeleteFalse(transactionId);
    }

    /**
     * Lưu account mới
     */
    public ProductVariantAccount save(ProductVariantAccount account) {
        return accountRepository.save(account);
    }

    /**
     * Lưu nhiều account cùng lúc (khi seller upload file)
     */
    @Transactional
    public void saveAll(List<ProductVariantAccount> accounts) {
        accountRepository.saveAll(accounts);
    }

    /**
     * Đánh dấu account là Sold và gắn với transaction
     */
    @Transactional
    public void markAsSold(Long accountId, Long transactionId) {
        ProductVariantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setStatus(ProductVariantAccount.AccountStatus.Sold);
        account.setTransactionId(transactionId);
        account.setUpdatedAt(new Date());

        accountRepository.save(account);
    }

    /**
     * Xóa mềm account
     */
    @Transactional
    public void softDelete(Long accountId, Long deletedBy) {
        ProductVariantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setDelete(true);
        account.setDeletedBy(deletedBy);
        account.setUpdatedAt(new Date());

        accountRepository.save(account);
    }

    /**
     * Kích hoạt account (đánh dấu đã dùng)
     */
    @Transactional
    public void activateAccount(Long accountId) {
        ProductVariantAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setActivated(true);
        account.setActivatedAt(new Date());
        account.setUpdatedAt(new Date());

        accountRepository.save(account);
    }

    public ProductVariantAccount findById(Long id) {
        return accountRepository.findById(id).orElse(null);
    }
}