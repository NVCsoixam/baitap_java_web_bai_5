# 1. Dùng Maven để build dự án ra file WAR
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 2. Đưa file WAR đã build vào Tomcat 10 để chạy
FROM tomcat:10.1-jdk17
# Xóa ứng dụng mặc định của Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*
# Đổi tên file WAR thành ROOT.war để chạy ngay ở đường dẫn gốc /
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
CMD ["catalina.sh", "run"]