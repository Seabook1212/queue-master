# Multi-stage build for Spring Boot 3.x with Java 17
# Stage 1: Build with Maven
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

ENV	SERVICE_USER=myuser \
	SERVICE_UID=10001 \
	SERVICE_GROUP=mygroup \
	SERVICE_GID=10001

RUN	groupadd -g ${SERVICE_GID} ${SERVICE_GROUP} && \
	useradd -g ${SERVICE_GROUP} -u ${SERVICE_UID} -M -s /usr/sbin/nologin ${SERVICE_USER} && \
	apt-get update && \
	apt-get install -y libcap2-bin && \
	setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java)) && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/app
COPY --from=builder /build/target/*.jar ./app.jar

RUN	chown -R ${SERVICE_USER}:${SERVICE_GROUP} ./app.jar

USER ${SERVICE_USER}

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","./app.jar", "--port=80"]
#image: weaveworksdemos/queue-master:0.3.1
#image: seabook1111/queue-master:inject-12-31-v6
#image/rabbitmq: rabbitmq:3.6.8-management