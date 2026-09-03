package com.skateboard.podcast.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes a business event for other services to react to. This service
 * states what happened; it does not know or care what anyone does with it —
 * no notification, recipient or push concept crosses this boundary.
 */
public interface PublishDomainEventPort {

    /**
     * @param eventId     stable across re-emissions of the same fact, so a
     *                    consumer can recognise a duplicate
     * @param routingKey  which business event this is, version included
     * @param payload     the event body
     * @return true only if the broker confirmed it took the message; false
     *         means the caller must treat the event as still owed
     */
    boolean publish(UUID eventId,
                    String eventType,
                    int version,
                    UUID tenantId,
                    Instant occurredAt,
                    String routingKey,
                    Map<String, Object> payload);
}
