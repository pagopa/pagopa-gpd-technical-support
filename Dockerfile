#
# Build
#
FROM maven:3.9.5-amazoncorretto-17-al2023@sha256:eeaa7ab572d931f7273fc5cf31429923f172091ae388969e11f42ec6dd817d74 AS buildtime
WORKDIR /build
COPY . .
RUN mvn clean package -Dmaven.test.skip=true

#
# Runtime
#
FROM ghcr.io/pagopa/docker-base-springboot-openjdk17:v2.2.0@sha256:b866656c31f2c6ebe6e78b9437ce930d6c94c0b4bfc8e9ecc1076a780b9dfb18
WORKDIR /app
COPY --chown=spring:spring --from=buildtime /build/target/*.jar application.jar

# https://github.com/moby/moby/issues/37965#issuecomment-426853382
RUN true

EXPOSE 8080

USER spring

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/application.jar"]