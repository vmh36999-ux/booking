package com.bookingplatform.inventory.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật job định kỳ (@Scheduled) và ShedLock (@SchedulerLock).
 *
 * ShedLock lưu khóa trong bảng shedlock của chính PostgreSQL này.
 * usingDbTime(): dùng đồng hồ của DATABASE
 * để tính hạn khóa, nên các instance lệch giờ nhau cũng không sao.
 * defaultLockAtMostFor: nếu một instance chết giữa chừng thì khóa tự nhả sau
 * tối đa 5 phút.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
