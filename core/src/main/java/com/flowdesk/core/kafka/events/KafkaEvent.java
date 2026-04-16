package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for all Kafka event schemas.
 * Every event carries a {@code version} field (default: 1) to support
 * schema evolution and backward-compatible upgrades.
 */
public abstract class KafkaEvent {

    @JsonProperty("version")
    private int version = 1;

    protected KafkaEvent() {}

    protected KafkaEvent(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
