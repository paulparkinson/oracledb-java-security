FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -ntp verify
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/workload-identity-oke-0.1.0.jar app.jar
COPY --from=build /build/target/lib lib
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "demo.WorkloadIdentityDemo"]
CMD ["check"]
