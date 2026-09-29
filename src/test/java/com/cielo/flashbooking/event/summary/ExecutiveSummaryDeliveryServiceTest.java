package com.cielo.flashbooking.event.summary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ExecutiveSummaryDeliveryServiceTest {

    private final ExecutiveSummaryReportStore reportStore = mock(ExecutiveSummaryReportStore.class);
    private final DiscordSummaryPublisher publisher = mock(DiscordSummaryPublisher.class);
    private final ExecutiveSummaryProperties properties = new ExecutiveSummaryProperties();
    private final ExecutiveSummaryDeliveryService service =
            new ExecutiveSummaryDeliveryService(reportStore, publisher, properties);
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void resetConfiguration() {
        properties.getDiscord().setWebhookSecretArn("");
    }

    @Test
    void missingSecretMarksNotConfiguredWithoutLookingUpOrPosting() {
        service.deliver(eventId);

        verify(reportStore).markDeliveryNotConfigured(eventId);
        verify(reportStore, never()).beginDelivery(eventId);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void persistsUnknownBeforeSinglePostAndPersistsConfirmedOutcome() {
        properties.getDiscord().setWebhookSecretArn("arn:aws:secretsmanager:sa-east-1:123:secret:discord");
        when(reportStore.beginDelivery(eventId)).thenReturn(Optional.of("# Evento\nResultado"));
        when(publisher.publish(any(), any())).thenReturn(DiscordSummaryPublisher.DeliveryStatus.SENT);

        service.deliver(eventId);

        InOrder order = inOrder(reportStore, publisher);
        order.verify(reportStore).beginDelivery(eventId);
        order.verify(publisher).publish("arn:aws:secretsmanager:sa-east-1:123:secret:discord", "# Evento\nResultado");
        order.verify(reportStore).finishDelivery(eventId, DiscordSummaryPublisher.DeliveryStatus.SENT);
    }

    @Test
    void doesNotPostWhenAnotherDeliveryHasAlreadyStarted() {
        properties.getDiscord().setWebhookSecretArn("arn:aws:secretsmanager:sa-east-1:123:secret:discord");
        when(reportStore.beginDelivery(eventId)).thenReturn(Optional.empty());

        service.deliver(eventId);

        verify(publisher, never()).publish(any(), any());
        verify(reportStore, never()).finishDelivery(any(), any());
    }
}
