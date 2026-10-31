FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml ./
COPY wallet-api/pom.xml wallet-api/pom.xml
COPY fake-services-api/pom.xml fake-services-api/pom.xml
COPY wallet-api/src wallet-api/src
RUN mvn -B -pl wallet-api -am package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /src/wallet-api/target/wallet-api-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 5080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
