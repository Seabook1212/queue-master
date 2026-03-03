package works.weave.socks.queuemaster.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import brave.Span;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.spring.rabbit.SpringRabbitTracing;
import io.micrometer.observation.ObservationPredicate;
import works.weave.socks.queuemaster.logging.FailureClassifier;
import works.weave.socks.queuemaster.logging.TraceExceptionTagger;

/**
 * Configuration for RabbitMQ distributed tracing with Micrometer/Brave in
 * Spring Boot 3.x
 */
@Configuration
public class TracingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfiguration.class);
    private static final String CONTAINER_NAME = readEnv("CONTAINER_NAME");
    private static final String POD_NAME = readEnv("POD_NAME");
    private static final String POD_NAMESPACE = readEnv("POD_NAMESPACE");
    private static final String NODE_NAME = readEnv("NODE_NAME");
    private final TraceExceptionTagger traceExceptionTagger;

    public TracingConfiguration(TraceExceptionTagger traceExceptionTagger) {
        this.traceExceptionTagger = traceExceptionTagger;
    }

    @Bean
    SpringRabbitTracing springRabbitTracing(Tracing tracing) {
        logger.info("Initializing SpringRabbitTracing for distributed tracing");
        return SpringRabbitTracing.newBuilder(tracing)
                .remoteServiceName("rabbitmq")
                .build();
    }

    @Bean
    SpanHandler spanTagNormalizationHandler() {
        return new SpanHandler() {
            @Override
            public boolean end(TraceContext context, MutableSpan span, Cause cause) {
                addTagIfAbsent(span, "container", CONTAINER_NAME);
                addTagIfAbsent(span, "pod", POD_NAME);
                addTagIfAbsent(span, "namespace", POD_NAMESPACE);
                addTagIfAbsent(span, "node", NODE_NAME);

                if (span.kind() == null) {
                    span.tag("span.kind", "internal");
                }

                if (span.kind() == Span.Kind.CONSUMER) {
                    // Avoid emitting "peer.service" from remote service mapping for consumer spans.
                    span.remoteServiceName(null);
                    span.removeTag("peer.service");

                    String destination = firstNonBlank(
                            span.tag("messaging.destination"),
                            span.tag("rabbit.queue"),
                            span.tag("rabbit.routing_key"));
                    if (destination != null) {
                        span.tag("messaging.destination", destination);
                    }
                }

                return true;
            }
        };
    }

    private static String readEnv(String variableName) {
        String value = System.getenv(variableName);
        return value == null || value.isBlank() ? null : value;
    }

    private static void addTagIfAbsent(MutableSpan span, String tagName, String value) {
        if (value != null && span.tag(tagName) == null) {
            span.tag(tagName, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
        } catch (NoSuchBeanDefinitionException e) {
            // Use default converter if custom one not found
            logger.warn("event=message_converter_missing component=rabbit_template usingDefaultConverter=true");
        } catch (Exception e) {
            logger.warn(
                    "event=message_converter_resolution_failed component=rabbit_template errorType={} exceptionClass={}",
                    FailureClassifier.classify(e),
                    e.getClass().getSimpleName(),
                    e);
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
        factory.setErrorHandler(rabbitListenerErrorHandler());

        // Get the message converter bean if it exists
        try {
            factory.setMessageConverter(beanFactory.getBean("jsonMessageConverter",
                    org.springframework.amqp.support.converter.MessageConverter.class));
        } catch (NoSuchBeanDefinitionException e) {
            logger.warn("event=message_converter_missing component=rabbit_listener usingDefaultConverter=true");
        } catch (Exception e) {
            logger.warn(
                    "event=message_converter_resolution_failed component=rabbit_listener errorType={} exceptionClass={}",
                    FailureClassifier.classify(e),
                    e.getClass().getSimpleName(),
                    e);
        }

        // Decorate the factory with tracing support
        SimpleRabbitListenerContainerFactory tracingFactory = springRabbitTracing
                .decorateSimpleRabbitListenerContainerFactory(factory);
        logger.info("SimpleRabbitListenerContainerFactory decorated with tracing support");

        return tracingFactory;
    }

    private ConditionalRejectingErrorHandler rabbitListenerErrorHandler() {
        return new ConditionalRejectingErrorHandler() {
            @Override
            protected void log(Throwable t) {
                traceExceptionTagger.tagException(t);

                Message failedMessage = null;
                if (t instanceof ListenerExecutionFailedException listenerExecutionFailedException) {
                    failedMessage = listenerExecutionFailedException.getFailedMessage();
                }

                MessageProperties properties = failedMessage != null ? failedMessage.getMessageProperties() : null;

                TracingConfiguration.logger.error(
                        "event=rabbitmq_consume_failed dependency=rabbitmq queue={} consumerQueue={} routingKey={} messageId={} correlationId={} errorType={} exceptionClass={}",
                        RabbitMqConfiguration.queueName,
                        properties != null ? properties.getConsumerQueue() : null,
                        properties != null ? properties.getReceivedRoutingKey() : null,
                        properties != null ? properties.getMessageId() : null,
                        properties != null ? properties.getCorrelationId() : null,
                        FailureClassifier.classify(t),
                        t.getClass().getSimpleName(),
                        t);
            }
        };
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
