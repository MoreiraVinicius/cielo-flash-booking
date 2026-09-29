package com.cielo.flashbooking.adapter.out.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.AlarmHistoryItem;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmHistoryRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmHistoryResponse;
import software.amazon.awssdk.services.cloudwatch.model.HistoryItemType;
import tools.jackson.databind.json.JsonMapper;

class CloudWatchOperationalSignalsReaderTest {

    private final CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
    private final ExecutiveSummaryProperties properties = new ExecutiveSummaryProperties();
    private final CloudWatchOperationalSignalsReader reader = new CloudWatchOperationalSignalsReader(
            cloudWatchClient, properties, JsonMapper.builder().build());
    private final Instant from = Instant.parse("2026-09-28T12:00:00Z");
    private final Instant to = from.plusSeconds(1800);

    @Test
    void requestsBoundedStateHistoryAndKeepsOnlyAlarmTransitionsWithFriendlyLabels() {
        properties.getOperationalSignals().setAlarms(List.of(alarm("demo-api-5xx", "Gateway com falhas temporarias")));
        when(cloudWatchClient.describeAlarmHistory(any(DescribeAlarmHistoryRequest.class)))
                .thenReturn(DescribeAlarmHistoryResponse.builder()
                        .alarmHistoryItems(
                                history(from.plusSeconds(10), "OK", "technical state details"),
                                history(from.plusSeconds(20), "ALARM", "technical alarm details"))
                        .build());

        var result = reader.read(from, to);

        assertThat(result.status())
                .isEqualTo(com.cielo.flashbooking.event.summary.OperationalSignalResult.Status.OBSERVED);
        assertThat(result.signals())
                .containsExactly(new com.cielo.flashbooking.event.summary.OperationalSignal(
                        "Gateway com falhas temporarias", from.plusSeconds(20)));
        var request = org.mockito.ArgumentCaptor.forClass(DescribeAlarmHistoryRequest.class);
        org.mockito.Mockito.verify(cloudWatchClient).describeAlarmHistory(request.capture());
        assertThat(request.getValue().alarmName()).isEqualTo("demo-api-5xx");
        assertThat(request.getValue().historyItemType()).isEqualTo(HistoryItemType.STATE_UPDATE);
        assertThat(request.getValue().maxRecords()).isEqualTo(10);
        assertThat(request.getValue().startDate()).isEqualTo(from);
        assertThat(request.getValue().endDate()).isEqualTo(to);
    }

    @Test
    void distinguishesNoTransitionsFromUnavailableOrPartialHistory() {
        properties.getOperationalSignals().setAlarms(List.of(alarm("queue-age", "Fila com atraso")));
        when(cloudWatchClient.describeAlarmHistory(any(DescribeAlarmHistoryRequest.class)))
                .thenReturn(DescribeAlarmHistoryResponse.builder().build());
        assertThat(reader.read(from, to).status())
                .isEqualTo(com.cielo.flashbooking.event.summary.OperationalSignalResult.Status.EMPTY);

        when(cloudWatchClient.describeAlarmHistory(any(DescribeAlarmHistoryRequest.class)))
                .thenReturn(
                        DescribeAlarmHistoryResponse.builder().nextToken("more").build());
        assertThat(reader.read(from, to).status())
                .isEqualTo(com.cielo.flashbooking.event.summary.OperationalSignalResult.Status.PARTIAL);

        when(cloudWatchClient.describeAlarmHistory(any(DescribeAlarmHistoryRequest.class)))
                .thenThrow(new RuntimeException("offline"));
        assertThat(reader.read(from, to).status())
                .isEqualTo(com.cielo.flashbooking.event.summary.OperationalSignalResult.Status.UNAVAILABLE);
    }

    @Test
    void marksMalformedHistoryAsPartialInsteadOfClaimingNoAlarms() {
        properties.getOperationalSignals().setAlarms(List.of(alarm("queue-age", "Fila com atraso")));
        when(cloudWatchClient.describeAlarmHistory(any(DescribeAlarmHistoryRequest.class)))
                .thenReturn(DescribeAlarmHistoryResponse.builder()
                        .alarmHistoryItems(AlarmHistoryItem.builder()
                                .timestamp(from.plusSeconds(15))
                                .historyData("not-json")
                                .build())
                        .build());

        assertThat(reader.read(from, to).status())
                .isEqualTo(com.cielo.flashbooking.event.summary.OperationalSignalResult.Status.PARTIAL);
    }

    private ExecutiveSummaryProperties.Alarm alarm(String name, String label) {
        var alarm = new ExecutiveSummaryProperties.Alarm();
        alarm.setName(name);
        alarm.setLabel(label);
        return alarm;
    }

    private AlarmHistoryItem history(Instant timestamp, String state, String details) {
        return AlarmHistoryItem.builder()
                .timestamp(timestamp)
                .historySummary(details)
                .historyData("""
                        {"newState":{"stateValue":"%s","stateReason":"%s"}}
                        """.formatted(state, details))
                .build();
    }
}
