package com.flowdesk.inventory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.outbox.OutboxRelayService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryOutboxRelayScheduler extends OutboxRelayService<InventoryOutboxEvent> {

    private final InventoryOutboxRepository repository;

    public InventoryOutboxRelayScheduler(InventoryOutboxRepository repository,
                                          KafkaTemplate<String, String> kafkaTemplate,
                                          ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
        this.repository = repository;
    }

    @Override protected List<InventoryOutboxEvent> findUnpublished() { return repository.findUnpublished(); }
    @Override protected void save(InventoryOutboxEvent event) { repository.save(event); }

    @Scheduled(fixedDelay = 500)
    public void run() { relay(); }
}
