FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY jharness2-engine/pom.xml jharness2-engine/
COPY jharness2-core/pom.xml jharness2-core/
COPY jharness2-storage/pom.xml jharness2-storage/
COPY jharness2-web/pom.xml jharness2-web/
RUN mvn dependency:go-offline -q || true
COPY . .
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S jharness && adduser -S jharness -G jharness
COPY --from=build /app/jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data && chown -R jharness:jharness /app
USER jharness
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
