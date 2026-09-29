package com.cielo.flashbooking.adapter.out.cloudwatch;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import com.cielo.flashbooking.event.summary.OperationalSignal;
import com.cielo.flashbooking.event.summary.OperationalSignalResult;
import com.cielo.flashbooking.event.summary.OperationalSignalsReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmHistoryRequest;
import software.amazon.awssdk.services.cloudwatch.model.HistoryItemType;
import software.amazon.awssdk.services.cloudwatch.model.ScanBy;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile({"worker", "all"})
class CloudWatchOperationalSignalsReader implements OperationalSignalsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudWatchOperationalSignalsReader.class);
    private static final int HISTORY_LIMIT_PER_ALARM = 10;
    private static final int MAX_ALARMS = 12;
    private static final int MAX_SIGNALS = 2;

    private final CloudWatchClient cloudWatchClient;
    private final ExecutiveSummaryProperties properties;
    private final JsonMapper jsonMapper;

    CloudWatchOperationalSignalsReader(
            CloudWatchClient cloudWatchClient, ExecutiveSummaryProperties properties, JsonMapper jsonMapper) {
        this.cloudWatchClient = cloudWatchClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public OperationalSignalResult read(Instant from, Instant to) {
        List<ExecutiveSummaryProperties.Alarm> alarms =
                properties.getOperationalSignals().getAlarms();
        if (alarms == null || alarms.isEmpty()) {
            return OperationalSignalResult.unavailable();
        }

        List<OperationalSignal> signals = new ArrayList<>();
        boolean partial = alarms.size() > MAX_ALARMS;
        int successfulQueries = 0;
        for (ExecutiveSummaryProperties.Alarm alarm :
                alarms.stream().limit(MAX_ALARMS).toList()) {
            try {
                var response = cloudWatchClient.describeAlarmHistory(DescribeAlarmHistoryRequest.builder()
                        .alarmName(alarm.getName())
                        .historyItemType(HistoryItemType.STATE_UPDATE)
                        .startDate(from)
                        .endDate(to)
                        .scanBy(ScanBy.TIMESTAMP_ASCENDING)
                        .maxRecords(HISTORY_LIMIT_PER_ALARM)
                        .build());
                successfulQueries++;
                partial |= response.nextToken() != null;
                for (var item : response.alarmHistoryItems()) {
                    Boolean alarmTransition = isAlarmTransition(item.historyData());
                    if (alarmTransition == null) {
                        partial = true;
                        continue;
                    }
                    if (item.timestamp() == null) {
                        partial = true;
                        continue;
                    }
                    if (!alarmTransition) {
                        continue;
                    }
                    signals.add(new OperationalSignal(alarm.getLabel(), item.timestamp()));
                }
            } catch (RuntimeException exception) {
                partial = true;
                LOGGER.warn(
                        "Operational signal unavailable: {}",
                        exception.getClass().getSimpleName());
            }
        }
        if (successfulQueries == 0) {
            return OperationalSignalResult.unavailable();
        }

        signals.sort(Comparator.comparing(OperationalSignal::occurredAt));
        List<OperationalSignal> limited = signals.stream().limit(MAX_SIGNALS).toList();
        OperationalSignalResult.Status status = partial
                ? OperationalSignalResult.Status.PARTIAL
                : limited.isEmpty() ? OperationalSignalResult.Status.EMPTY : OperationalSignalResult.Status.OBSERVED;
        return new OperationalSignalResult(status, limited);
    }

    private Boolean isAlarmTransition(String historyData) {
        if (historyData == null || historyData.isBlank()) {
            return null;
        }
        try {
            String state = jsonMapper
                    .readTree(historyData)
                    .path("newState")
                    .path("stateValue")
                    .asString();
            return "ALARM".equals(state);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Operational signal history could not be interpreted: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }
}
