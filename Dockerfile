# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY libs libs
RUN mvn -q install:install-file -Dfile=libs/org.deeplearning4j.jar \
    -DgroupId=local.phd -DartifactId=deeplearning4j-evalmetric -Dversion=1.0 -Dpackaging=jar
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=1000:1000 /app/target/tsaas.jar tsaas.jar
USER 1000
ENV PORT=7860 JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 7860
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar tsaas.jar"]
