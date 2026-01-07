package works.weave.socks.queuemaster;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import works.weave.socks.shipping.entities.Shipment;

@Component
public class ShippingTaskHandler {

	private static final Logger logger = LoggerFactory.getLogger(ShippingTaskHandler.class);

	@Autowired
	DockerSpawner docker;

	@Autowired(required = false)
	private Tracer tracer;

	@RabbitListener(queues = "shipping-task", containerFactory = "tracingRabbitListenerContainerFactory")
	public void handleMessage(Shipment shipment) {
		// Log trace information if available
		if (tracer != null && tracer.currentSpan() != null) {
			logger.info("Received shipment task: {} [traceId={}, spanId={}]",
					shipment.getName(),
					tracer.currentSpan().context().traceId(),
					tracer.currentSpan().context().spanId());
		} else {
			logger.info("Received shipment task: {}", shipment.getName());
		}
		// docker.init();
		// docker.spawn();
	}
}
