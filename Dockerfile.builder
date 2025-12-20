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

# Create Maven cache directory
RUN mkdir -p /root/.m2

# Copy pom.xml first to leverage Docker layer caching
WORKDIR /build
COPY pom.xml .

# Download dependencies to cache them in the image
RUN mvn dependency:go-offline -B

# Set working directory for builds
WORKDIR /build