FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install netcat-openbsd (recommended)
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh

# COPY target/watcher-0.0.1-SNAPSHOT.jar /app/

# CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "watcher-0.0.1-SNAPSHOT.jar"]

COPY target/*.jar app.jar

CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "app.jar"]