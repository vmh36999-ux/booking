package com.bookingplatform.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Tự đăng ký filter cho mọi service có thêm common-lib. */
@AutoConfiguration
public class CommonWebAutoConfiguration {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
