FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
# PDF 내보내기용 한글 폰트
RUN apt-get update && apt-get install -y --no-install-recommends fonts-nanum && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
