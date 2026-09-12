package com.skateboard.podcast.adapter.out.messaging;

import com.skateboard.podcast.infrastructure.messaging.EventTopology;
import com.skateboard.podcast.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per CLAUDE.md: publisher confirms are what let this class tell a genuinely
 * accepted message apart from one the broker never took — a send the broker
 * didn't confirm must come back false so the reconciliation job retries it,
 * never true.
 */
@ExtendWith(MockitoExtension.class)
class RabbitDomainEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitDomainEventPublisher publisher;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private void init() {
        publisher = new RabbitDomainEventPublisher(rabbitTemplate);
    }

    /** Runs the OperationsCallback handed to invoke(), the same way the real RabbitTemplate would. */
    private void stubInvokeToRunCallback() {
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });
    }

    @Test
    void returnsTrueWhenBrokerConfirmsThePublish() {
        init();
        stubInvokeToRunCallback();
        when(rabbitTemplate.waitForConfirms(5_000L)).thenReturn(true);

        boolean result = publisher.publish(UUID.randomUUID(), "PODCAST_PUBLISHED", 1, UUID.randomUUID(),
                Instant.now(), "podcast.published.v1", Map.of("a", "b"));

        assertThat(result).isTrue();
        verify(rabbitTemplate).convertAndSend(eq(EventTopology.EXCHANGE), eq("podcast.published.v1"),
                any(Map.class), any(MessagePostProcessor.class));
    }

    /**
     * A confirm callback that comes back negative is not a broker acceptance —
     * per CLAUDE.md, a confirm only says the broker took the message, so a
     * negative ack must be treated the same as "not confirmed".
     */
    @Test
    void returnsFalseWhenBrokerDoesNotConfirmThePublish() {
        init();
        stubInvokeToRunCallback();
        when(rabbitTemplate.waitForConfirms(5_000L)).thenReturn(false);

        boolean result = publisher.publish(UUID.randomUUID(), "PODCAST_PUBLISHED", 1, UUID.randomUUID(),
                Instant.now(), "podcast.published.v1", Map.of("a", "b"));

        assertThat(result).isFalse();
    }

    /**
     * The podcast itself was already saved successfully — a broker problem
     * (e.g. connection refused) must be swallowed, not rethrown, so it can't
     * fail the request that saved the post.
     */
    @Test
    void returnsFalseAndSwallowsTheExceptionWhenTheTemplateFails() {
        init();
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker unreachable"));

        boolean result = publisher.publish(UUID.randomUUID(), "PODCAST_PUBLISHED", 1, UUID.randomUUID(),
                Instant.now(), "podcast.published.v1", Map.of("a", "b"));

        assertThat(result).isFalse();
    }

    /**
     * CorrelationIdFilter puts the request's correlation id in MDC so log
     * lines across services can be tied together; this publisher must carry
     * it onto the outgoing message as a header.
     */
    @Test
    void stampsTheCorrelationIdHeaderWhenOneIsPresentInMdc() {
        init();
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        stubInvokeToRunCallback();
        when(rabbitTemplate.waitForConfirms(5_000L)).thenReturn(true);

        publisher.publish(UUID.randomUUID(), "PODCAST_PUBLISHED", 1, UUID.randomUUID(),
                Instant.now(), "podcast.published.v1", Map.of("a", "b"));

        org.mockito.ArgumentCaptor<MessagePostProcessor> captor =
                org.mockito.ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class), captor.capture());

        Message message = MessageBuilder.withBody(new byte[0]).build();
        Message processed = captor.getValue().postProcessMessage(message);

        assertThat(processed.getMessageProperties().getHeaders())
                .containsEntry("X-Correlation-Id", "corr-123");
    }

    /** No correlation id in MDC (e.g. a scheduled job, not an HTTP request) means no header is added. */
    @Test
    void doesNotStampACorrelationIdHeaderWhenNoneIsPresent() {
        init();
        stubInvokeToRunCallback();
        when(rabbitTemplate.waitForConfirms(5_000L)).thenReturn(true);

        publisher.publish(UUID.randomUUID(), "PODCAST_PUBLISHED", 1, UUID.randomUUID(),
                Instant.now(), "podcast.published.v1", Map.of("a", "b"));

        org.mockito.ArgumentCaptor<MessagePostProcessor> captor =
                org.mockito.ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class), captor.capture());

        Message message = MessageBuilder.withBody(new byte[0]).build();
        Message processed = captor.getValue().postProcessMessage(message);

        assertThat(processed.getMessageProperties().getHeaders()).doesNotContainKey("X-Correlation-Id");
    }
}
