# Build stage - use pre-built builder image
ARG LCL_PLATFORM
FROM --platform=$LCL_PLATFORM plusmin/pm-backend-builder:latest AS builder

# Set working directory to match the expected path
WORKDIR /build

# Copy source code
COPY src ./src
COPY pom.xml .

# Build the application (dependencies already cached in builder image)
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-ubi9-minimal AS runtime

ENV HOME=/home/appuser
ENV APP_HOME=$HOME/app
ENV LOGS_HOME=$HOME/logs

RUN mkdir -p $APP_HOME
RUN mkdir -p $LOGS_HOME
RUN groupadd -g 1001 appuser && useradd -u 1001 -g 1001 appuser
RUN chown -R appuser:appuser $HOME
USER appuser
WORKDIR $HOME

# Copy the built jar from the builder stage
COPY --from=builder /build/target/pm-backend-*.jar $APP_HOME/app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar", "app/app.jar"]
