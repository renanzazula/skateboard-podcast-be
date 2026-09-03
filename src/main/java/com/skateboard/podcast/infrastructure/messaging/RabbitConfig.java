package com.skateboard.podcast.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producer-side wiring.
 *
 * <p>Publisher confirms are the point of this class. Without them
 * {@code convertAndSend} returns as soon as the bytes are handed to the
 * client, so a broker that never accepted the message would still look like a
 * success — and this service would mark the post notified and never mention it
 * again. With confirms, {@code waitForConfirms} can tell the difference, which
 * is what makes "set notified_at only on a confirmed publish" mean anything.
 *
 * <p>A confirm says the broker took the message, not that a queue was bound to
 * receive it. Publishing before skateboard-notification-be has ever declared
 * its queue therefore succeeds and goes nowhere; that is a deployment-ordering
 * concern, and the reason PODCAST_NOTIFICATIONS_ENABLED starts false.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange applicationEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    /**
     * Uses the application's ObjectMapper so the JavaTimeModule Boot configured
     * is in play; without it an Instant serializes as an epoch array that
     * consumers cannot bind to an ISO-8601 field.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
