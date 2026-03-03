package works.weave.socks.queuemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import works.weave.socks.shipping.entities.Shipment;

@Component
public class ShippingTaskHandler {

	private static final Logger logger = LoggerFactory.getLogger(ShippingTaskHandler.class);

	// @Autowired
	// DockerSpawner docker;

	@RabbitListener(queues = "shipping-task", containerFactory = "tracingRabbitListenerContainerFactory")
	public void handleMessage(Shipment shipment) {
		String shipmentId = shipment != null ? shipment.getId() : null;
		String shipmentName = shipment != null ? shipment.getName() : null;

		logger.info(
				"event=shipment_task_received queue=shipping-task shipmentId={} shipmentName={}",
				shipmentId,
				shipmentName);
		// docker.init();
		// docker.spawn();
	}
}
