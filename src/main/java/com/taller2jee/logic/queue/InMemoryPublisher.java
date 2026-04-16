package com.taller2jee.logic.queue;

import com.taller2jee.common.model.EmailEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryPublisher implements QueuePublisher {
    private final List<EmailEvent> messages = new ArrayList<>();

    @Override
    public void publishEmailResult(EmailEvent event) {
        messages.add(event);
    }

    public List<EmailEvent> messages() {
        return Collections.unmodifiableList(messages);
    }
}
