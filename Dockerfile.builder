# Builder image with Maven and dependencies pre-cached
FROM eclipse-temurin:21-jdk-ubi9-minimal

# Install Maven
RUN microdnf update -y && \
    microdnf install -y wget tar gzip && \
    microdnf clean all

ENV MAVEN_VERSION=3.9.6
ENV MAVEN_HOME=/opt/maven
ENV PATH=$PATH:$MAVEN_HOME/bin

RUN wget https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz && \
    tar -xzf apache-maven-$MAVEN_VERSION-bin.tar.gz -C /opt && \
    mv /opt/apache-maven-$MAVEN_VERSION $MAVEN_HOME && \
    rm apache-maven-$MAVEN_VERSION-bin.tar.gz

# Create Maven cache directory and set it as a volume for persistence
RUN mkdir -p /root/.m2/repository
VOLUME /root/.m2/repository

# Copy pom.xml first to leverage Docker layer caching
WORKDIR /build
COPY pom.xml .

# Download all dependencies and plugin dependencies to cache them in the image
# This ensures that all subsequent builds won't need to download dependencies again
RUN mvn dependency:go-offline -B && \
    mvn dependency:resolve-sources -B && \
    mvn dependency:resolve -B && \
    mvn org.springframework.boot:spring-boot-maven-plugin:help -B && \
    mvn org.springdoc:springdoc-openapi-maven-plugin:help -B && \
    mvn org.apache.maven.plugins:maven-compiler-plugin:help -B && \
    mvn org.owasp:dependency-check-maven:help -B

# Pre-compile and cache plugin executions by running a dry-run compile
# This downloads any additional plugin dependencies that might be needed
RUN mvn clean compile -B -Dmaven.main.skip=true -Dmaven.test.skip=true || true

# Set working directory for builds  
WORKDIR /app