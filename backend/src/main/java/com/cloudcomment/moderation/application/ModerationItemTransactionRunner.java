package com.cloudcomment.moderation.application;

import com.cloudcomment.moderation.domain.ModerationAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

interface ModerationItemTransactionRunner {

    ModerationAction run(Supplier<ModerationAction> operation);
}

@Component
class RequiresNewModerationItemTransactionRunner implements ModerationItemTransactionRunner {

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModerationAction run(Supplier<ModerationAction> operation) {
        return operation.get();
    }
}
