
# FROM eclipse-temurin:21-jdk

# WORKDIR /app

# # Install dependencies
# RUN apt-get update && \
#     apt-get install -y netcat-openbsd && \
#     rm -rf /var/lib/apt/lists/*

# # Environment variables
# ENV SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# # Copy artifacts
# COPY wait-for.sh /wait-for.sh
# RUN ls -la /wait-for.sh && \
#     file /wait-for.sh && \
#     cat /wait-for.sh
# # RUN chmod +x /wait-for.sh
# # COPY target/JobConsumerSvc-0.0.1-SNAPSHOT.jar /app/
# COPY target/*.jar app.jar
# # Correct CMD syntax
# # CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "JobConsumerSvc-0.0.1-SNAPSHOT.jar"]
# CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "app.jar"]


FROM eclipse-temurin:21-jdk

WORKDIR /app


## Install dependencies
#RUN apt-get update && \
#    apt-get install -y netcat-openbsd && \
#    rm -rf /var/lib/apt/lists/*

# Install dependencies with error handling
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/* && \
    { [ -e /usr/bin/netcat ] || ln -s /usr/bin/nc /usr/bin/netcat; }


# Environment variables
ENV SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092


# Copy and prepare wait-for.sh
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && \
    sed -i 's/\r$//' /wait-for.sh 2>/dev/null || true

# Copy application
COPY target/*.jar app.jar

# Entrypoint
CMD ["/bin/sh", "-c", "/wait-for.sh kafka 9092 && exec java -jar app.jar"]

