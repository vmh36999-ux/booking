package com.bookingplatform.inventory.infrastructure.persistence;

import com.bookingplatform.inventory.domain.port.ProcessedEventRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private final JdbcTemplate jdbc;

    public JdbcProcessedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ON CONFLICT DO NOTHING: một câu lệnh nguyên tử, hai consumer nhận cùng sự
     * kiện thì chỉ một bên được true.
     */
    @Override
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING", eventId);
        return inserted == 1;
    }
}
