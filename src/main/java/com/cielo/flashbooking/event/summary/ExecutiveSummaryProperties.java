package com.cielo.flashbooking.event.summary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "executive-summary")
public class ExecutiveSummaryProperties {

    @Valid
    private Bedrock bedrock = new Bedrock();

    @Valid
    private OperationalSignals operationalSignals = new OperationalSignals();

    @Valid
    private Discord discord = new Discord();

    public Bedrock getBedrock() {
        return bedrock;
    }

    public void setBedrock(Bedrock bedrock) {
        this.bedrock = bedrock;
    }

    public OperationalSignals getOperationalSignals() {
        return operationalSignals;
    }

    public void setOperationalSignals(OperationalSignals operationalSignals) {
        this.operationalSignals = operationalSignals;
    }

    public Discord getDiscord() {
        return discord;
    }

    public void setDiscord(Discord discord) {
        this.discord = discord;
    }

    public static class Discord {

        private String webhookSecretArn = "";

        public String getWebhookSecretArn() {
            return webhookSecretArn;
        }

        public void setWebhookSecretArn(String webhookSecretArn) {
            this.webhookSecretArn = webhookSecretArn;
        }
    }

    public static class Bedrock {

        @NotBlank
        private String region = "sa-east-1";

        @NotBlank
        private String modelId = "amazon.nova-micro-v1:0";

        private Duration apiTimeout = Duration.ofSeconds(25);

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public Duration getApiTimeout() {
            return apiTimeout;
        }

        public void setApiTimeout(Duration apiTimeout) {
            this.apiTimeout = apiTimeout;
        }

        @AssertTrue(message = "executive-summary.bedrock.api-timeout must be positive and at most 60 seconds")
        public boolean isApiTimeoutValid() {
            return apiTimeout != null
                    && !apiTimeout.isNegative()
                    && !apiTimeout.isZero()
                    && apiTimeout.compareTo(Duration.ofSeconds(60)) <= 0;
        }
    }

    public static class OperationalSignals {

        @Valid
        @Size(max = 12)
        private List<Alarm> alarms = new ArrayList<>();

        public List<Alarm> getAlarms() {
            return alarms;
        }

        public void setAlarms(List<Alarm> alarms) {
            this.alarms = alarms;
        }
    }

    public static class Alarm {

        @NotBlank
        private String name;

        @NotBlank
        @Size(max = 100)
        private String label;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
