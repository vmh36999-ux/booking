package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Quét và cho hết hạn mọi hold đã quá hạn, theo từng lượt (batch).
 * Cố ý KHÔNG đánh dấu @Transactional: mỗi hold tự có transaction riêng bên
 * trong HoldExpirer.
 */
@Service
public class ExpireHolds {
    private static final Logger log = LoggerFactory.getLogger(ExpireHolds.class);

    /** Chặn vòng lặp vô tận nếu có chuyện bất thường. */
    private static final int MAX_BATCHES = 100;

    private final HoldRepository holds;
    private final HoldExpirer expirer;
    private final Clock clock;
    private final int batchSize;

    public ExpireHolds(HoldRepository holds, HoldExpirer expirer, Clock clock,
            @Value("${app.inventory.expiry.batch-size:100}") int batchSize) {
        this.holds = holds;
        this.expirer = expirer;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Trả về số hold đã cho hết hạn. */
    public int execute() {
        int expired = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Hold> due = holds.findDueForExpiry(clock.instant(), batchSize);
            if (due.isEmpty()) {
                break;
            }
            int expiredInBatch = 0;
            for (Hold hold : due) {
                try {
                    if (expirer.expire(hold.id())) {
                        expiredInBatch++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to expire hold {}: {}", hold.id(), e.toString());
                }
            }
            expired += expiredInBatch;
            // Hết hàng đợi, hoặc cả lượt không tiến triển được (tránh lặp lại mãi các hold
            // bị lỗi)
            if (due.size() < batchSize || expiredInBatch == 0) {
                break;
            }
        }
        return expired;
    }
}
