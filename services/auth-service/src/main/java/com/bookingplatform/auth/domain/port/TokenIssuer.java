package com.bookingplatform.auth.domain.port;

import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;

public interface TokenIssuer {

    IssuedToken issue(Credential credential);
}