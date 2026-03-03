package works.weave.socks.queuemaster.controllers;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import works.weave.socks.queuemaster.entities.HealthCheck;
import works.weave.socks.queuemaster.logging.FailureClassifier;
import works.weave.socks.queuemaster.logging.TraceExceptionTagger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HealthCheckController {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    TraceExceptionTagger traceExceptionTagger;

    @Value("${spring.rabbitmq.host}")
    private String rabbitMqHost;

    private static final int RABBITMQ_TIMEOUT_MS = 5000;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET, path = "/health")
    public
    @ResponseBody
    Map<String, List<HealthCheck>> getHealth() {
        Map<String, List<HealthCheck>> map = new HashMap<String, List<HealthCheck>>();
        List<HealthCheck> healthChecks = new ArrayList<HealthCheck>();
        Date dateNow = Calendar.getInstance().getTime();

        HealthCheck app = new HealthCheck("queue-master", "OK", dateNow);
        HealthCheck rabbitmq = new HealthCheck("queue-master-rabbitmq", "OK", dateNow);

        try {
            this.rabbitTemplate.execute(new ChannelCallback<String>() {
                @Override
                public String doInRabbit(Channel channel) throws Exception {
                    Map<String, Object> serverProperties = channel.getConnection().getServerProperties();
                    return serverProperties.get("version").toString();
                }
            });
        } catch ( AmqpException e ) {
            rabbitmq.setStatus("err");
            traceExceptionTagger.tagException(e);
            logger.warn(
                    "event=rabbitmq_health_check_failed dependency=rabbitmq operation=health_check host={} timeoutMs={} errorType={} exceptionClass={}",
                    rabbitMqHost,
                    RABBITMQ_TIMEOUT_MS,
                    FailureClassifier.classify(e),
                    e.getClass().getSimpleName(),
                    e);
        }

        healthChecks.add(app);
        healthChecks.add(rabbitmq);

        map.put("health", healthChecks);
        return map;
    }
}
