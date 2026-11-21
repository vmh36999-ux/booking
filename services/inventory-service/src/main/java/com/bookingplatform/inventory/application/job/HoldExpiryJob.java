package com.bookingplatform.inventory.application.job;

import com.bookingplatform.inventory.application.usecase.ExpireHolds;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job chạy định kỳ (mặc định mỗi phút): cho hết hạn các hold quá hạn và trả
 * phòng về.
 *
 * @SchedulerLock (ShedLock): chạy nhiều bản inventory-service cùng lúc thì mỗi
 *                lượt chỉ MỘT bản được chạy job.
 *                Các bản khác thấy khóa đang bị giữ thì bỏ qua lượt đó.
 *                - lockAtMostFor : nếu bản đang chạy chết giữa chừng, khóa tự
 *                nhả sau tối đa chừng này
 *                - lockAtLeastFor: giữ khóa tối thiểu chừng này, để hai bản
 *                không chạy nối tiếp nhau trong cùng một phút
 */
@Component
@ConditionalOnProperty(name = "app.inventory.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final ExpireHolds expireHolds;

    public HoldExpiryJob(ExpireHolds expireHolds) {
        this.expireHolds = expireHolds;
    }

    @Scheduled(fixedDelayString = "${app.inventory.expiry.interval:PT1M}")
    @SchedulerLock(name = "inventory-hold-expiry", lockAtMostFor = "PT5M", lockAtLeastFor = "${app.inventory.expiry.lock-at-least-for:PT20S}")
    public void run() {
        int expired = expireHolds.execute();
        if (expired > 0) {
            log.info("Expired {} holds and released rooms", expired);
        }
    }
}
