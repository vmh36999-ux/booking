package com.bookingplatform.property.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.property.infrastructure.seed.SeedDataGenerator;
import com.bookingplatform.property.infrastructure.seed.SeedDataGenerator.City;
import com.bookingplatform.property.infrastructure.seed.SeedDataGenerator.SeedProperty;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra bộ sinh dữ liệu: ra đúng 10.000 id khác nhau, đủ 20 thành phố, 2 đến
 * 5 loại phòng, tiện nghi nằm trong danh mục, tọa độ quanh trung tâm thành phố,
 * cùng số thứ tự thì cùng kết quả (5 test).
 * SeedDataGeneratorTest
 */
class SeedDataGeneratorTest {

    private final SeedDataGenerator generator = new SeedDataGenerator();

    @Test
    void sameIndexAlwaysGivesTheSameProperty() {
        assertThat(generator.generate(42)).isEqualTo(generator.generate(42));
        assertThat(generator.generate(42)).isNotEqualTo(generator.generate(43));
    }

    @Test
    void tenThousandPropertiesHaveUniqueIdsAndCoverTwentyCities() {
        Set<UUID> ids = new HashSet<>();
        Set<String> cities = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            SeedProperty p = generator.generate(i);
            ids.add(p.id());
            cities.add(p.city());
        }

        assertThat(ids).hasSize(10_000);
        assertThat(cities).hasSize(20);
    }

    @Test
    void everyPropertyHasTwoToFiveRoomTypesWithUniqueIds() {
        Set<UUID> roomIds = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            SeedProperty p = generator.generate(i);
            assertThat(p.roomTypes()).hasSizeBetween(2, 5);
            p.roomTypes().forEach(r -> {
                assertThat(roomIds.add(r.id())).as("id loại phòng trùng").isTrue();
                assertThat(r.basePriceMinor()).isPositive();
                assertThat(r.totalRooms()).isBetween(2, 30);
                assertThat(r.maxGuests()).isBetween(2, 6);
            });
        }
    }

    @Test
    void amenitiesAreThreeToNineAndAllInTheCatalog() {
        for (int i = 0; i < 2_000; i++) {
            SeedProperty p = generator.generate(i);
            assertThat(p.amenities()).hasSizeBetween(3, 9).doesNotHaveDuplicates();
            assertThat(SeedDataGenerator.AMENITY_CODES).containsAll(p.amenities());
        }
    }

    @Test
    void coordinatesStayNearTheCityCenter() {
        Map<String, City> byName = generator.cities().stream()
                .collect(Collectors.toMap(City::name, Function.identity()));
        for (int i = 0; i < 2_000; i++) {
            SeedProperty p = generator.generate(i);
            City c = byName.get(p.city());
            assertThat(Math.abs(p.latitude() - c.lat())).isLessThan(0.1);
            assertThat(Math.abs(p.longitude() - c.lng())).isLessThan(0.1);
            assertThat(p.currency()).isEqualTo(c.currency());
        }
    }
}
