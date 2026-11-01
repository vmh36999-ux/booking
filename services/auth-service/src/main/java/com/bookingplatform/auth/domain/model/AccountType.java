package com.bookingplatform.auth.domain.model;

public enum AccountType {
    GUEST, HOST;

    public Role toRole() {
        return this == HOST ? Role.HOST : Role.GUEST;
    }
}
