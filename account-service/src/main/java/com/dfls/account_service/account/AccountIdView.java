package com.dfls.account_service.account;

import java.util.UUID;

public interface AccountIdView {
    UUID getId();
    String getCurrency();
}