package com.bookingplatform.user.domain.exception;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("User profile not found. If you just registered, your profile is being created. Please try again in a few seconds.");
    }
}