package com.bookingplatform.inventory.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HostInventoryResponse(UUID roomTypeId, LocalDate from, LocalDate to, int nights, int totalRooms) {
}
