FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install dependencies
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

# Environment variables
ENV SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Copy artifacts
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh
COPY target/JobConsumerSvc-0.0.1-SNAPSHOT.jar /app/

# Correct CMD syntax
CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "JobConsumerSvc-0.0.1-SNAPSHOT.jar"]