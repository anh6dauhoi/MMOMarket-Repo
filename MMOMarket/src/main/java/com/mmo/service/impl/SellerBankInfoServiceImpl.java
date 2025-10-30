package com.mmo.service.impl;

import com.mmo.entity.SellerBankInfo;
import com.mmo.entity.User;
import com.mmo.service.SellerBankInfoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SellerBankInfoServiceImpl implements SellerBankInfoService {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public SellerBankInfo saveOrUpdateBankInfo(User user, String bankName, String accountNumber, String accountHolder, String branch) throws Exception {
        // Find or create SellerBankInfo for user
        SellerBankInfo bankInfo = entityManager.createQuery("SELECT s FROM SellerBankInfo s WHERE s.user = :user", SellerBankInfo.class).setParameter("user", user).getResultStream().findFirst().orElse(null);
        boolean isNew = (bankInfo == null);
        if (isNew) {
            bankInfo = new SellerBankInfo();
            bankInfo.setUser(user);
        }
        bankInfo.setBankName(bankName);
        bankInfo.setAccountNumber(accountNumber);
        if (accountHolder != null && !accountHolder.isBlank()) {
            bankInfo.setAccountHolder(accountHolder);
        }
        if (branch != null) {
            bankInfo.setBranch(branch);
        }
        if (isNew) {
            entityManager.persist(bankInfo);
        } else {
            entityManager.merge(bankInfo);
        }
        return bankInfo;
    }

    @Override
    public List<SellerBankInfo> findAllByUser(User user) {
        return entityManager.createQuery("SELECT s FROM SellerBankInfo s WHERE s.user = :user AND (s.isDelete = false OR s.isDelete IS NULL)", SellerBankInfo.class).setParameter("user", user).getResultList();
    }
}
