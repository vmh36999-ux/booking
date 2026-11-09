package com.bookingplatform.user.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bookingplatform.user.domain.port.ProcessedEventRepository;

@Repository
public class JdbcProcessedEventRepository implements ProcessedEventRepository {
    private final JdbcTemplate jdbc;

    public JdbcProcessedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ON CONFLICT DO NOTHING: nếu eventId đã có thì không chèn và trả 0 dòng.
     * Là MỘT câu lệnh nguyên tử nên hai consumer nhận cùng sự kiện cùng lúc thì chỉ
     * một bên được true.
     */
    @Override
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.update(
                "INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING", eventId);
        return inserted == 1;
    }
}
