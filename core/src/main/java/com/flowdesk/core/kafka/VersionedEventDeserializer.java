package com.flowdesk.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowdesk.core.kafka.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Routes an incoming Kafka message to the correct typed handler based on the
 * {@code version} field embedded in the JSON payload.
 *
 * <p>Usage:
 * <pre>{@code
 * VersionedEventDeserializer deserializer = new VersionedEventDeserializer();
 * deserializer.register("hr.employee.changed", 1, json -> {
 *     EmployeeChangedEvent event = deserializer.parse(json, EmployeeChangedEvent.class);
 *     // handle v1 event
 * });
 * deserializer.dispatch("hr.employee.changed", rawJson);
 * }</pre>
 *
 * <p>When a new schema version is introduced, register an additional handler for the new
 * version number. Old consumers continue to use the v1 handler until they are updated.
 */
public class VersionedEventDeserializer {

    private static final Logger log = LoggerFactory.getLogger(VersionedEventDeserializer.class);

    private final ObjectMapper objectMapper;

    /**
     * Registry: topic → (version → handler).
     * Handlers receive the raw JSON string and are responsible for parsing it.
     */
    private final Map<String, Map<Integer, Function<String, Void>>> registry = new HashMap<>();

    public VersionedEventDeserializer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public VersionedEventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Register a handler for a specific topic and schema version.
     *
     * @param topic   Kafka topic name (e.g. {@code "hr.employee.changed"})
     * @param version Schema version this handler supports
     * @param handler Function that receives the raw JSON payload string
     */
    public void register(String topic, int version, Function<String, Void> handler) {
        registry.computeIfAbsent(topic, k -> new HashMap<>()).put(version, handler);
    }

    /**
     * Dispatch a raw JSON payload to the registered handler for the given topic and version.
     * The version is read from the {@code "version"} field in the JSON (defaults to 1 if absent).
     *
     * @param topic   Kafka topic name
     * @param payload Raw JSON string
     * @throws UnknownEventVersionException if no handler is registered for the detected version
     */
    public void dispatch(String topic, String payload) {
        int version = extractVersion(payload);
        Map<Integer, Function<String, Void>> versionHandlers = registry.get(topic);

        if (versionHandlers == null) {
            log.warn("No handlers registered for topic '{}' — skipping message", topic);
            return;
        }

        Function<String, Void> handler = versionHandlers.get(version);
        if (handler == null) {
            throw new UnknownEventVersionException(
                    "No handler registered for topic '" + topic + "' version " + version
                    + ". Registered versions: " + versionHandlers.keySet());
        }

        handler.apply(payload);
    }

    /**
     * Parse a raw JSON string into a typed event class.
     *
     * @param json       Raw JSON payload
     * @param eventClass Target event class (must extend {@link KafkaEvent})
     * @param <T>        Event type
     * @return Parsed event instance
     */
    public <T extends KafkaEvent> T parse(String json, Class<T> eventClass) {
        try {
            return objectMapper.readValue(json, eventClass);
        } catch (Exception e) {
            throw new EventDeserializationException(
                    "Failed to deserialize event to " + eventClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Extract the {@code version} field from a JSON payload.
     * Returns {@code 1} if the field is absent (backward compatibility with pre-versioned events).
     */
    private int extractVersion(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode versionNode = node.get("version");
            return (versionNode != null && versionNode.isInt()) ? versionNode.asInt() : 1;
        } catch (Exception e) {
            log.warn("Could not parse version from payload, defaulting to 1: {}", e.getMessage());
            return 1;
        }
    }

    // ── Built-in topic constants ──────────────────────────────────────────────

    public static final String TOPIC_HR_EMPLOYEE_CHANGED      = "hr.employee.changed";
    public static final String TOPIC_HR_REVIEW_SUBMITTED      = "hr.review.submitted";
    public static final String TOPIC_INVENTORY_LOW_STOCK      = "inventory.low-stock";
    public static final String TOPIC_SALES_ORDER_CONFIRMED    = "sales.order.confirmed";
    public static final String TOPIC_SALES_CREDIT_HOLD        = "sales.credit-hold";
    public static final String TOPIC_ACCOUNTING_INVOICE_OVERDUE = "accounting.invoice.overdue";
    public static final String TOPIC_AUDIT_EVENTS             = "audit.events";

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static class UnknownEventVersionException extends RuntimeException {
        public UnknownEventVersionException(String message) {
            super(message);
        }
    }

    public static class EventDeserializationException extends RuntimeException {
        public EventDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
