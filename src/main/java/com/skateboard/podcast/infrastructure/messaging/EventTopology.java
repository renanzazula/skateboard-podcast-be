package com.skateboard.podcast.infrastructure.messaging;

/**
 * The shared exchange and this service's routing keys.
 *
 * <p>Only the exchange is declared here. The queues bound to it belong to the
 * consumers, and a producer that knew their names would be coupled to who is
 * listening — the thing publishing events instead of calling
 * skateboard-notification-be directly is meant to avoid (spec §7).
 */
public final class EventTopology {

    /** One topic exchange for every business event on the platform. */
    public static final String EXCHANGE = "application.events";

    /**
     * Version travels in the routing key, so a v2 payload can be published
     * alongside v1 and bound separately during a migration rather than
     * breaking every consumer at once.
     */
    public static final String PODCAST_PUBLISHED_ROUTING_KEY = "podcast.published.v1";

    private EventTopology() {
    }
}
