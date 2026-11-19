package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.domain.exception.HoldNotFoundException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetHold {

    private final HoldRepository holds;

    public GetHold(HoldRepository holds) {
        this.holds = holds;
    }

    @Transactional(readOnly = true)
    public Hold execute(UUID holdId) {
        return holds.findById(holdId).orElseThrow(HoldNotFoundException::new);
    }
}
