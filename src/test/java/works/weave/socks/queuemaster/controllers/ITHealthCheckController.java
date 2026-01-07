package works.weave.socks.queuemaster.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import works.weave.socks.queuemaster.entities.HealthCheck;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

@SpringBootTest
public class ITHealthCheckController {

    @Autowired
    private HealthCheckController healthCheckController;

    @Test
    public void getHealthCheck() throws Exception {
        Map<String, List<HealthCheck>> healthChecks = healthCheckController.getHealth();
        assertThat(healthChecks.get("health").size(), is(equalTo(2)));
    }
}
