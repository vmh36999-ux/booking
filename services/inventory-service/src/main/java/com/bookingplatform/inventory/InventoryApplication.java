package com.bookingplatform.inventory;

import org.springframework.context.annotation.Import;
import com.bookingplatform.common.web.CommonExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Import(CommonExceptionHandler.class)
@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
