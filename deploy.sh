#!/bin/bash

# Tự động dừng script nếu có lệnh nào bị lỗi (exit code khác 0)
set -e

# Đường dẫn thư mục backend trên VPS
BACKEND_DIR="/var/www/backend"

echo "==========================================="
echo "   BẮT ĐẦU ĐỒNG BỘ VÀ DEPLOY BACKEND       "
echo "==========================================="

# 1. Di chuyển vào thư mục code
cd "$BACKEND_DIR"

# 2. Tải code mới nhất từ GitHub về
echo "--> 1. Đang lấy code mới nhất từ GitHub..."
git pull origin main

# 3. Biên dịch dự án bằng Maven
echo "--> 2. Đang biên dịch code sang file JAR..."
mvn clean package -DskipTests

# 4. Copy file JAR mới ra thư mục chạy chính
echo "--> 3. Đang cập nhật file chạy JAR..."
cp target/shop-acc-game-0.0.1-SNAPSHOT.jar shop-acc-game.jar

# 5. Khởi động lại dịch vụ backend
echo "--> 4. Đang khởi động lại dịch vụ shop-backend.service..."
systemctl restart shop-backend.service

# 6. Hiển thị trạng thái hoạt động
echo "--> 5. Đang kiểm tra trạng thái hoạt động..."
sleep 2 # Chờ 2 giây để java kịp khởi động
systemctl status shop-backend.service --no-pager

echo "==========================================="
echo "   DEPLOY HOÀN TẤT THÀNH CÔNG! 🎉          "
echo "==========================================="
