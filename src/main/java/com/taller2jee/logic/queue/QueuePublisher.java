package com.taller2jee.logic.queue;

import com.taller2jee.common.model.EmailEvent;

public interface QueuePublisher {
    void publishEmailResult(EmailEvent event);
}
