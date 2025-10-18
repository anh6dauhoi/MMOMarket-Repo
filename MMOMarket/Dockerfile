# Bước 1: Chọn image nền (base image)
# Sử dụng Java 17 (JDK) vì dự án của bạn dùng JDK 17
FROM eclipse-temurin:17-jdk-jammy

# Bước 2: Tạo thư mục làm việc bên trong image
WORKDIR /app

# Bước 3: Copy file .jar đã được build vào image
# Lệnh này sẽ lấy file .jar từ thư mục target (bạn vừa build)
# và đổi tên nó thành app.jar để dễ chạy
COPY target/MMOMarket-0.0.1-SNAPSHOT.jar app.jar

# Bước 4: Mở cổng (expose port) mà Spring Boot đang chạy
# Cổng mặc định của Spring Boot là 8080
EXPOSE 8080

# Bước 5: Lệnh để chạy ứng dụng khi container khởi động
ENTRYPOINT ["java", "-jar", "/app/app.jar"]