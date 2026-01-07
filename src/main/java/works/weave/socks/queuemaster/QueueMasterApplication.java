package works.weave.socks.queuemaster;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class QueueMasterApplication {
	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(QueueMasterApplication.class, args);
	}
}
