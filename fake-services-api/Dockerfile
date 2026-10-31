FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml ./
COPY wallet-api/pom.xml wallet-api/pom.xml
COPY fake-services-api/pom.xml fake-services-api/pom.xml
COPY fake-services-api/src fake-services-api/src
RUN mvn -B -pl fake-services-api -am package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /src/fake-services-api/target/fake-services-api-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 5081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
