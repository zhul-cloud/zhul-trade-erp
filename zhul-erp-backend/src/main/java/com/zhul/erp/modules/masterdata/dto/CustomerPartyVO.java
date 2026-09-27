package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

@Data
public class CustomerPartyVO {
    private Long id;
    private Integer partyType;
    private String companyName;
    private String country;
    private String state;
    private String city;
    private String postcode;
    private String address;
    private String contactName;
    private String phone;
    private String email;
    private String taxId;
    private String destinationPort;
    private Boolean defaultParty;
    private String remark;
}
