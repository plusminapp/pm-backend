ARG PLATFORM

FROM eclipse-temurin:21-jre-ubi9-minimal

ARG JAR_FILE

ENV HOME=/home/appuser
ENV APP_HOME=$HOME/app
ENV LOGS_HOME=$HOME/logs

RUN mkdir -p $APP_HOME
RUN mkdir -p $LOGS_HOME
RUN groupadd -g 1001 appuser && useradd -u 1001 -g 1001 appuser
RUN chown -R appuser:appuser $HOME
USER appuser
WORKDIR $HOME

COPY $JAR_FILE $APP_HOME/app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar", "app/app.jar"]
