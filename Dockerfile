# 构建阶段：Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom，利用 Docker 层缓存下载依赖
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# 运行阶段：JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --home-dir /app appuser

COPY --from=build /build/target/*.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER appuser

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
