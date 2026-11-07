FROM eclipse-temurin:26-jdk
WORKDIR /app
COPY . .
RUN javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
CMD ["java", "-cp", "out", "crys.sims.Main"]