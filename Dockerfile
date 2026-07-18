# =========================
# Stage 1: Build Frontend
# =========================
FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend

# 安装前端依赖（利用 Docker 缓存）
COPY frontend/package*.json ./
RUN npm ci

# 构建前端
COPY frontend/ ./
RUN npm run build


# =========================
# Stage 2: Build Backend
# =========================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS backend-build

WORKDIR /app

# 先复制 pom.xml，缓存 Maven 依赖
COPY pom.xml ./

# 下载依赖（后续代码修改无需重新下载）
RUN mvn dependency:go-offline -B

# 再复制源码
COPY src ./src

# 复制前端构建产物到 Spring Boot 静态资源目录
COPY --from=frontend-build /app/src/main/resources/static ./src/main/resources/static

# 打包 Spring Boot
RUN mvn clean package -DskipTests -B


# =========================
# Stage 3: Runtime
# =========================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 Jar 包
COPY --from=backend-build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 4545

# JVM 参数（可按服务器内存调整）
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
