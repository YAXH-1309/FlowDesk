package com.flowdesk.accounting.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.outbox.OutboxRelayService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountingOutboxRelayScheduler extends OutboxRelayService<AccountingOutboxEvent> {

    private final AccountingOutboxRepository repository;

    public AccountingOutboxRelayScheduler(AccountingOutboxRepository repository,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
        this.repository = repository;
    }

    @Override protected List<AccountingOutboxEvent> findUnpublished() { return repository.findUnpublished(); }
    @Override protected void save(AccountingOutboxEvent event) { repository.save(event); }

    @Scheduled(fixedDelay = 500)
    public void run() { relay(); }
}
