package com.mmo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerRegistrationDTO {
    private Long id;
    private Long customerId;
    private String shopName;
    private String email;
    private String phone;
    private String registrationDate;
    private String status;
    private String contractName;
    private String contractUrl;
    private String signedContractName;
    private String signedContractUrl;
    private String reason;
    private String description;
}
