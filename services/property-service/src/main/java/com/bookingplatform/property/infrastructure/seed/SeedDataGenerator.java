package com.bookingplatform.property.infrastructure.seed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Sinh dữ liệu mẫu hoàn toàn ĐỊNH TRƯỚC (deterministic): cùng số thứ tự thì
 * luôn ra cùng khách sạn, cùng id.
 * Nhờ vậy chạy seed nhiều lần không tạo trùng (id giống nhau, INSERT bỏ qua).
 */
public final class SeedDataGenerator {

    public static final List<String> AMENITY_CODES = List.of(
            "WIFI", "POOL", "PARKING", "BREAKFAST", "AIR_CONDITIONING", "GYM", "SPA", "RESTAURANT",
            "AIRPORT_SHUTTLE", "PET_FRIENDLY", "BEACH_ACCESS", "KITCHEN", "BALCONY", "FAMILY_ROOMS", "NON_SMOKING");

    public record City(String name, String country, double lat, double lng, String currency) {
    }

    public record SeedRoomType(UUID id, String name, int maxGuests, String bedConfig, int totalRooms,
            long basePriceMinor) {
    }

    public record SeedProperty(UUID id, UUID hostId, String name, String description, String city, String country,
            String address, double latitude, double longitude, String currency,
            List<String> amenities, List<SeedRoomType> roomTypes, Instant createdAt) {
    }

    private static final List<City> CITIES = List.of(
            new City("Hà Nội", "VN", 21.0278, 105.8342, "VND"),
            new City("Hồ Chí Minh", "VN", 10.7769, 106.7009, "VND"),
            new City("Đà Nẵng", "VN", 16.0544, 108.2022, "VND"),
            new City("Hội An", "VN", 15.8801, 108.3380, "VND"),
            new City("Nha Trang", "VN", 12.2388, 109.1967, "VND"),
            new City("Đà Lạt", "VN", 11.9404, 108.4583, "VND"),
            new City("Phú Quốc", "VN", 10.2899, 103.9840, "VND"),
            new City("Huế", "VN", 16.4637, 107.5909, "VND"),
            new City("Hạ Long", "VN", 20.9101, 107.1839, "VND"),
            new City("Sa Pa", "VN", 22.3364, 103.8438, "VND"),
            new City("Vũng Tàu", "VN", 10.3460, 107.0843, "VND"),
            new City("Hải Phòng", "VN", 20.8449, 106.6881, "VND"),
            new City("Cần Thơ", "VN", 10.0452, 105.7469, "VND"),
            new City("Phan Thiết", "VN", 10.9289, 108.1021, "VND"),
            new City("Quy Nhơn", "VN", 13.7765, 109.2237, "VND"),
            new City("Bangkok", "TH", 13.7563, 100.5018, "USD"),
            new City("Singapore", "SG", 1.3521, 103.8198, "USD"),
            new City("Tokyo", "JP", 35.6762, 139.6503, "USD"),
            new City("Seoul", "KR", 37.5665, 126.9780, "USD"),
            new City("Bali", "ID", -8.6705, 115.2126, "USD"));

    private static final int HOST_COUNT = 50;
    private static final String[] PREFIXES = { "Sunrise", "Golden", "Blue Lagoon", "Palm", "Lotus", "Riverside",
            "Central", "Royal", "Garden", "Skyline", "Harbor", "Pearl", "Emerald", "Orchid", "Bamboo" };
    private static final String[] SUFFIXES = { "Hotel", "Resort", "Boutique Hotel", "Homestay", "Villa", "Residence" };
    private static final String[] STREETS = { "Trần Phú", "Lê Lợi", "Nguyễn Huệ", "Hai Bà Trưng", "Võ Nguyên Giáp",
            "Phạm Văn Đồng", "Nguyễn Trãi", "Lý Thường Kiệt" };
    private static final String[] ROOM_NAMES = { "Standard Double", "Deluxe Twin", "Superior King", "Family Suite",
            "Studio", "Ocean View Suite", "Executive Room" };
    private static final String[] BEDS = { "1 giường đôi", "2 giường đơn", "1 giường đôi và 1 giường đơn",
            "2 giường đôi" };
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    public List<City> cities() {
        return CITIES;
    }

    public SeedProperty generate(int index) {
        Random rnd = new Random(1_000_003L * (index + 1L));
        City city = CITIES.get(index % CITIES.size()); // chia đều các thành phố

        String name = PREFIXES[rnd.nextInt(PREFIXES.length)] + " " + city.name() + " "
                + SUFFIXES[rnd.nextInt(SUFFIXES.length)];
        double lat = city.lat() + (rnd.nextDouble() - 0.5) * 0.12; // lệch tối đa khoảng 6 km quanh trung tâm
        double lng = city.lng() + (rnd.nextDouble() - 0.5) * 0.12;
        String address = (1 + rnd.nextInt(300)) + " " + STREETS[rnd.nextInt(STREETS.length)] + ", " + city.name();

        List<String> pool = new ArrayList<>(AMENITY_CODES);
        Collections.shuffle(pool, rnd);
        List<String> amenities = new ArrayList<>(pool.subList(0, 3 + rnd.nextInt(7))); // 3 đến 9 tiện nghi
        Collections.sort(amenities);

        boolean vnd = "VND".equals(city.currency());
        long unit = vnd ? 10_000L : 100L;
        long base = vnd ? 300_000L + rnd.nextInt(1_200_000) : 3_500L + rnd.nextInt(8_000);

        int roomCount = 2 + rnd.nextInt(4); // 2 đến 5 loại phòng
        List<String> roomNames = new ArrayList<>(List.of(ROOM_NAMES));
        Collections.shuffle(roomNames, rnd);
        List<SeedRoomType> rooms = new ArrayList<>();
        for (int j = 0; j < roomCount; j++) {
            double price = base * (1 + 0.45 * j) * (0.9 + 0.2 * rnd.nextDouble());
            long priceMinor = Math.max(unit, Math.round(price / unit) * unit);
            rooms.add(new SeedRoomType(uuid("room", index * 10L + j), roomNames.get(j), 2 + rnd.nextInt(5),
                    BEDS[rnd.nextInt(BEDS.length)], 2 + rnd.nextInt(29), priceMinor));
        }

        String description = "Cơ sở lưu trú " + name + " tại " + city.name() + ", có " + roomCount
                + " loại phòng. Dữ liệu mẫu số " + index + ".";
        return new SeedProperty(uuid("property", index), uuid("host", index % HOST_COUNT), name, description,
                city.name(), city.country(), address, lat, lng, city.currency(), amenities, rooms,
                BASE_TIME.plusSeconds(index));
    }

    private static UUID uuid(String kind, long n) {
        return UUID.nameUUIDFromBytes((kind + ":" + n).getBytes(StandardCharsets.UTF_8));
    }
}
