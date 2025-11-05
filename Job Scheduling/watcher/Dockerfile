
# FROM eclipse-temurin:21-jdk

# WORKDIR /app

# # Install netcat-openbsd (recommended)
# RUN apt-get update && \
#     apt-get install -y netcat-openbsd && \
#     rm -rf /var/lib/apt/lists/*

# COPY wait-for.sh /wait-for.sh
# RUN chmod +x /wait-for.sh

# # COPY target/watcher-0.0.1-SNAPSHOT.jar /app/

# # CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "watcher-0.0.1-SNAPSHOT.jar"]

# COPY target/*.jar app.jar

# CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "app.jar"]


FROM eclipse-temurin:21-jdk

WORKDIR /app


# Install dependencies with cleanup

RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*


# Copy and prepare wait-for script
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && \
    sed -i 's/\r$//' /wait-for.sh 2>/dev/null || true  # Handle Windows line endings

# Copy application JAR (using wildcard for version flexibility)
COPY target/*.jar app.jar

# Health check endpoint (adjust port as needed)
HEALTHCHECK --interval=30s --timeout=3s \
  CMD nc -z localhost 8080 || exit 1

# Entrypoint with proper signal handling
ENTRYPOINT ["/wait-for.sh", "kafka", "9092"]
CMD ["java", "-jar", "app.jar"]

