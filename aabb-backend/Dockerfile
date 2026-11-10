# Build: roda na arquitetura da máquina que compila (o .jar serve para qualquer plataforma)
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Runtime: imagem da plataforma de destino (ex: linux/arm64 do Raspberry Pi)
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 aabb
COPY --from=build /app/build/libs/*.jar app.jar
USER aabb
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
