package com.skateboard.podcast.adapter.out.messaging;

import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.infrastructure.messaging.EventTopology;
import com.skateboard.podcast.infrastructure.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RabbitDomainEventPublisher implements PublishDomainEventPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitDomainEventPublisher.class);

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public boolean publish(UUID eventId, String eventType, int version, UUID tenantId,
                            Instant occurredAt, String routingKey, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", version);
        envelope.put("tenantId", tenantId.toString());
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("payload", payload);

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        try {
            // invoke() pins one channel for the send and the confirm; without
            // it waitForConfirms has no channel to wait on and the return
            // value means nothing.
            Boolean confirmed = rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(EventTopology.EXCHANGE, routingKey, envelope, message -> {
                    if (correlationId != null) {
                        message.getMessageProperties().setHeader("X-Correlation-Id", correlationId);
                    }
                    return message;
                });
                return operations.waitForConfirms(CONFIRM_TIMEOUT_MS);
            });

            if (Boolean.TRUE.equals(confirmed)) {
                log.info("Published {} eventId={} routingKey={}", eventType, eventId, routingKey);
                return true;
            }
            log.error("Broker did not confirm {} eventId={} within {}ms", eventType, eventId, CONFIRM_TIMEOUT_MS);
            return false;
        } catch (Exception e) {
            // Never rethrown: the podcast was saved successfully and a broker
            // problem must not fail the request that saved it. The caller
            // leaves the post un-notified, and the reconciliation job retries.
            log.error("Could not publish {} eventId={}: {}", eventType, eventId, e.getMessage(), e);
            return false;
        }
    }
}
