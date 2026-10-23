# مرحله اول: کامپایل و ساخت بسته اجرایی (Build Stage)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# کپی فایل تنظیمات وابستگی‌ها جهت استفاده بهینه از کش داکر
COPY pom.xml .
RUN mvn dependency:go-offline -B

# کپی سورس‌کد و کامپایل پروژه
COPY src ./src
RUN mvn clean package -DskipTests

# مرحله دوم: ایمیج بهینه، امن و سبک زمان اجرا (Runtime Stage)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# ایجاد کاربر سیستمی غیر روت برای رعایت الزامات امنیتی
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# کپی فایل jar تولیدشده از مرحله قبل
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
