package com.bookingplatform.auth.domain.model;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** Email luôn lưu và so sánh dạng chữ thường, bỏ khoảng trắng hai đầu. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
