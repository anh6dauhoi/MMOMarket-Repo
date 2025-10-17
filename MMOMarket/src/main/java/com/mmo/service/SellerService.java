package com.mmo.service;

import com.mmo.entity.SellerRegistration;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface SellerService {
    void registerSeller(SellerRegistration sellerRegistration);

    Page<SellerRegistration> findAllRegistrations(String status, Pageable pageable);

    Optional<SellerRegistration> findById(Long id);

    SellerRegistration approve(Long id, MultipartFile contractFile) throws IOException;

    SellerRegistration reject(Long id, String reason);

    void activate(Long id);

    Resource loadContract(Long id, boolean signed) throws IOException;

    void submitSignedContract(MultipartFile file) throws IOException;

    SellerRegistration resubmit(String shopName, String description);
}
