package com.mmo.service;

import com.mmo.entity.SellerBankInfo;
import com.mmo.entity.User;

public interface SellerBankInfoService {
    SellerBankInfo saveOrUpdateBankInfo(User user, String bankName, String accountNumber, String accountHolder, String branch) throws Exception;
}

