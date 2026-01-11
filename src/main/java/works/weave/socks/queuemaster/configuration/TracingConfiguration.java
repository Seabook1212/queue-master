package works.weave.socks.queuemaster.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import brave.Tracing;
import brave.spring.rabbit.SpringRabbitTracing;
import io.micrometer.observation.ObservationPredicate;

/**
 * Configuration for RabbitMQ distributed tracing with Micrometer/Brave in
 * Spring Boot 3.x
 */
@Configuration
public class TracingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfiguration.class);

    @Bean
    SpringRabbitTracing springRabbitTracing(Tracing tracing) {
        logger.info("Initializing SpringRabbitTracing for distributed tracing");
        return SpringRabbitTracing.newBuilder(tracing)
                .remoteServiceName("rabbitmq")
                .build();
    }

    @Bean
    @Primary
    RabbitTemplate tracingRabbitTemplate(ConnectionFactory connectionFactory,
            SpringRabbitTracing springRabbitTracing,
            BeanFactory beanFactory) {
        logger.info("Creating tracing-enabled RabbitTemplate");
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        // Get the message converter bean if it exists
        try {
            rabbitTemplate.setMessageConverter(beanFactory.getBean("jsonMessageConverter",
                    org.springframework.amqp.support.converter.MessageConverter.class));
        } catch (Exception e) {
            // Use default converter if custom one not found
            logger.debug("Using default message converter");
        }

        // Add tracing interceptor
        rabbitTemplate = springRabbitTracing.decorateRabbitTemplate(rabbitTemplate);
        logger.info("RabbitTemplate decorated with tracing support");
        return rabbitTemplate;
    }

    @Bean
    @Primary
    SimpleRabbitListenerContainerFactory tracingRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SpringRabbitTracing springRabbitTracing,
            BeanFactory beanFactory) {
        logger.info("Creating tracing-enabled SimpleRabbitListenerContainerFactory");
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // Get the message converter bean if it exists
        try {
            factory.setMessageConverter(beanFactory.getBean("jsonMessageConverter",
                    org.springframework.amqp.support.converter.MessageConverter.class));
        } catch (Exception e) {
            logger.debug("Using default message converter for listener factory");
        }

        // Decorate the factory with tracing support
        SimpleRabbitListenerContainerFactory tracingFactory = springRabbitTracing
                .decorateSimpleRabbitListenerContainerFactory(factory);
        logger.info("SimpleRabbitListenerContainerFactory decorated with tracing support");

        return tracingFactory;
    }

    @Bean
    public ObservationPredicate skipHealthCheckTracing() {
        return (name, context) -> {
            // Only filter HTTP server observations
            if (context instanceof ServerRequestObservationContext) {
                ServerRequestObservationContext serverContext = (ServerRequestObservationContext) context;
                String uri = serverContext.getCarrier().getRequestURI();

                // List of endpoints to exclude from tracing
                boolean shouldSkip = uri != null && (uri.equals("/health") ||
                        uri.equals("/metrics") ||
                        uri.equals("/prometheus") ||
                        uri.startsWith("/actuator/"));

                if (shouldSkip) {
                    logger.trace("Skipping trace for endpoint: {}", uri);
                    return false; // Don't observe (skip tracing)
                }
            }

            return true; // Observe (create trace)
        };
    }
}
