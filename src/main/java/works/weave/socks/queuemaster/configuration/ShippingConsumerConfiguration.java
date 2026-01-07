package works.weave.socks.queuemaster.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for RabbitMQ message consumers
 * Note: Message listeners are now configured using @RabbitListener annotation
 * with automatic tracing support via TracingConfiguration
 */
@Configuration
public class ShippingConsumerConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ShippingConsumerConfiguration.class);

	public ShippingConsumerConfiguration() {
		logger.info("Initializing ShippingConsumerConfiguration with Micrometer Tracing auto-instrumentation");
	}
}
