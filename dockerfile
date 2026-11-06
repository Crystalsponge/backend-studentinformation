FROM eclipse-temurin:26-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out && javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")

# Stage 2: run
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY data ./data
CMD ["java", "-cp", "out", "crys.sims.Main"]