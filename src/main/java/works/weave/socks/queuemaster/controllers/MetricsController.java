package works.weave.socks.queuemaster.controllers;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom controller to expose Prometheus metrics at /metrics endpoint
 * In Spring Boot 3.x with Micrometer
 */
@RestController
public class MetricsController {

    @Autowired
    private MeterRegistry meterRegistry;

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        if (meterRegistry instanceof PrometheusMeterRegistry prometheusMeterRegistry) {
            return prometheusMeterRegistry.scrape();
        }
        return "# Prometheus metrics not available\n";
    }
}
