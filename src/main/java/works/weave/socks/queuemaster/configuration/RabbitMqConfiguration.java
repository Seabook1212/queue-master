package works.weave.socks.queuemaster.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.weave.socks.shipping.entities.Shipment;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfiguration {
    final static String queueName = "shipping-task";

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(host);
        connectionFactory.setCloseTimeout(5000);
        connectionFactory.setConnectionTimeout(5000);
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        return connectionFactory;
    }

    @Bean
    public AmqpAdmin amqpAdmin() {
        return new RabbitAdmin(connectionFactory());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        // Spring AMQP 2.x uses Jackson2JavaTypeMapper instead of DefaultClassMapper
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();

        // Map the type ID to the class
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("works.weave.socks.shipping.entities.Shipment", Shipment.class);
        typeMapper.setIdClassMapping(idClassMapping);

        // Set trusted packages for deserialization
        typeMapper.setTrustedPackages("works.weave.socks.shipping.entities");

        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    Queue queue() {
        return new Queue(queueName, false);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange("shipping-task-exchange");
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(queueName);
    }
}
