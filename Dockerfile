FROM jenkins/jenkins:lts

# Switch to root user to install Docker
USER root

# Update package list and install Docker CLI
RUN apt-get update && apt-get install -y docker.io

# Switch back to jenkins user for security
USER jenkins