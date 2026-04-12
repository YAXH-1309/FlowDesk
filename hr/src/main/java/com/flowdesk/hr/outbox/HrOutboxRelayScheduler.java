package com.flowdesk.hr.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.outbox.OutboxRelayService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HrOutboxRelayScheduler extends OutboxRelayService<HrOutboxEvent> {

    private final HrOutboxRepository repository;

    public HrOutboxRelayScheduler(HrOutboxRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
        this.repository = repository;
    }

    @Override
    protected List<HrOutboxEvent> findUnpublished() {
        return repository.findUnpublished();
    }

    @Override
    protected void save(HrOutboxEvent event) {
        repository.save(event);
    }

    @Scheduled(fixedDelay = 500)
    public void run() {
        relay();
    }
}
