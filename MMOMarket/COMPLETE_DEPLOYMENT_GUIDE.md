# 📚 HƯỚNG DẪN TOÀN DIỆN - ENCRYPTION FEATURE

## 📑 Mục Lục
1. [Chạy Local Development](#1-chạy-local-development)
2. [Deploy lên AWS Server](#2-deploy-lên-aws-server)
3. [Migration Dữ Liệu](#3-migration-dữ-liệu)
4. [Troubleshooting](#4-troubleshooting)
5. [Security Best Practices](#5-security-best-practices)

---

# 1. CHẠY LOCAL DEVELOPMENT

## 🚀 Quick Start - Cực Kỳ Đơn Giản!

Khi chạy **local**, bạn **KHÔNG CẦN** setup gì cả!

### Bước 1: Chạy Application
```bash
# Với Maven
mvn clean spring-boot:run

# Hoặc trong IntelliJ IDEA
# Click nút Run hoặc Shift+F10
```

✅ **Xong!** Application tự động:
- Generate encryption key cho development
- Mã hóa tất cả account data mới
- Giải mã khi đọc dữ liệu

### Bước 2: Kiểm Tra Hoạt Động

#### Test Import Account
1. Login với tài khoản seller
2. Vào **Product Management** → chọn product variant
3. Click **"Import Accounts"**
4. Upload file CSV với accounts
5. ✅ Data sẽ được mã hóa tự động!

#### Kiểm Tra Database
```sql
-- Mở MySQL Workbench hoặc DBeaver
USE mmomarket;
SELECT id, accountData FROM ProductVariantAccounts LIMIT 5;
```

**Kết quả mong đợi:**
```
id  | accountData
----|----------------------------------------------------------------
1   | AgxK3mQ9pL7rT2nY5wZ8... (Chuỗi Base64 dài ~60-80 ký tự)
2   | BpzL4nR0qM8sU3oA6xC9...
3   | CqyM5oS1rN9tV4pB7yD0...
```

❌ **KHÔNG được thấy**: `user1:password123` (plain text)
✅ **Phải thấy**: Chuỗi mã hóa Base64

#### Test Xem Account Trong App
1. Seller vào Product Detail → View Accounts
2. Dữ liệu hiển thị **bình thường** (username:password)
3. Customer mua hàng → nhận account **bình thường**
4. ✅ Tất cả hoạt động như trước!

### Bước 3: Test Encryption Utility (Optional)

```bash
# Terminal
cd src/main/java
javac com/mmo/util/EncryptionUtil.java
java com.mmo.util.EncryptionUtil
```

**Output:**
```
Original: Username: admin
Password: secret123
Email: test@example.com

Encrypted (Base64): AgxK3mQ9pL7rT2nY5wZ8BpzL4nR0qM8sU3oA...
Encrypted length: 76

Decrypted: Username: admin
Password: secret123
Email: test@example.com

Match: true

=== Generate New Key for Production ===
Set this as environment variable ENCRYPTION_KEY:
qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN==
```

## 🔄 Migration Dữ Liệu Cũ (Local)

Nếu database local có account data cũ (plain text) chưa mã hóa:

### Bước 1: Enable Migration
Mở `src/main/resources/application.properties`:
```properties
# Thêm hoặc sửa dòng này
migration.encrypt-accounts=true
```

### Bước 2: Restart Application
```bash
mvn spring-boot:run
```

### Bước 3: Theo Dõi Log
```
========================================
STARTING ACCOUNT DATA ENCRYPTION MIGRATION
========================================
Processing batch: 1 (100 accounts)
  Account #1 encrypted successfully
  Account #2 encrypted successfully
  ...
Progress: 100 processed, 100 encrypted, 0 skipped, 0 errors

Processing batch: 2 (100 accounts)
Progress: 200 processed, 200 encrypted, 0 skipped, 0 errors

========================================
MIGRATION COMPLETED SUCCESSFULLY
========================================
Total accounts processed: 250
Total encrypted: 250
Total already encrypted (skipped): 0
Total errors: 0

IMPORTANT: Please disable this migration by setting:
  migration.encrypt-accounts=false
========================================
```

### Bước 4: Disable Migration
Mở lại `application.properties`:
```properties
# Sửa lại thành false
migration.encrypt-accounts=false
```

### Bước 5: Restart Lần Cuối
```bash
mvn spring-boot:run
```

✅ **Hoàn tất!** Tất cả data đã được mã hóa.

## 🎯 Local Development Checklist

- [ ] Pull code mới từ Git
- [ ] Chạy `mvn clean spring-boot:run`
- [ ] Test import accounts
- [ ] Kiểm tra database (data phải mã hóa)
- [ ] Test customer purchase flow
- [ ] Verify accounts hiển thị đúng
- [ ] (Optional) Run migration nếu có data cũ

---

# 2. DEPLOY LÊN AWS SERVER

## 🌐 Kiến Trúc AWS Deployment

```
Internet
    ↓
AWS Load Balancer (HTTPS)
    ↓
EC2 Instance (Application Server)
    ↓
RDS MySQL (Database)
```

## 📋 Prerequisites

### 1. AWS Resources Cần Có
- ✅ EC2 Instance (Ubuntu 20.04 hoặc Amazon Linux 2)
- ✅ RDS MySQL Database
- ✅ Security Groups configured
- ✅ SSH key pair để access EC2

### 2. Server Requirements
- Java 17 or higher
- Maven 3.8+
- MySQL Client
- 2GB RAM minimum

## 🚀 Deployment Steps

### BƯỚC 1: Chuẩn Bị Encryption Key

#### 1.1 Generate Production Key
Trên **máy local** (KHÔNG phải server):
```bash
cd src/main/java
javac com/mmo/util/EncryptionUtil.java
java com.mmo.util.EncryptionUtil
```

Copy key từ output:
```
=== Generate New Key for Production ===
Set this as environment variable ENCRYPTION_KEY:
qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN==
```

#### 1.2 Lưu Key An Toàn
**Tạo file backup trên máy local:**
```bash
# Tạo thư mục bảo mật
mkdir -p ~/secure-backups/mmomarket

# Lưu key vào file
echo "ENCRYPTION_KEY=qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN==" > ~/secure-backups/mmomarket/encryption-key.txt

# Phân quyền chỉ owner đọc được
chmod 600 ~/secure-backups/mmomarket/encryption-key.txt
```

⚠️ **QUAN TRỌNG**: 
- Backup file này sang USB hoặc cloud storage riêng
- KHÔNG commit vào Git
- KHÔNG gửi qua email/chat

### BƯỚC 2: Connect Vào AWS EC2

```bash
# Từ máy local, SSH vào EC2
ssh -i "your-key.pem" ubuntu@ec2-xx-xx-xx-xx.compute.amazonaws.com

# Hoặc nếu dùng Amazon Linux
ssh -i "your-key.pem" ec2-user@ec2-xx-xx-xx-xx.compute.amazonaws.com
```

### BƯỚC 3: Setup Environment trên EC2

#### 3.1 Install Java 17
```bash
# Ubuntu
sudo apt update
sudo apt install openjdk-17-jdk -y

# Amazon Linux 2
sudo yum install java-17-amazon-corretto-devel -y

# Verify
java -version
# Output: openjdk version "17.0.x"
```

#### 3.2 Install Maven
```bash
# Ubuntu
sudo apt install maven -y

# Amazon Linux 2
sudo yum install maven -y

# Verify
mvn -version
```

#### 3.3 Set Environment Variable
```bash
# Mở file profile
sudo nano /etc/environment

# Thêm dòng này (thay YOUR_KEY bằng key thực)
ENCRYPTION_KEY="qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN=="

# Lưu file: Ctrl+O, Enter, Ctrl+X

# Load environment
source /etc/environment

# Verify
echo $ENCRYPTION_KEY
# Phải hiển thị key của bạn
```

**Hoặc set cho user hiện tại:**
```bash
# Thêm vào ~/.bashrc hoặc ~/.bash_profile
echo 'export ENCRYPTION_KEY="qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN=="' >> ~/.bashrc

# Load
source ~/.bashrc

# Verify
echo $ENCRYPTION_KEY
```

### BƯỚC 4: Deploy Application

#### 4.1 Clone Repository
```bash
# Tạo thư mục app
mkdir -p /opt/mmomarket
cd /opt/mmomarket

# Clone code (hoặc upload JAR file)
git clone https://github.com/your-org/mmomarket.git .

# Hoặc upload từ local
# Từ máy local:
# scp -i "your-key.pem" target/mmomarket-0.0.1-SNAPSHOT.jar ubuntu@ec2-xx-xx-xx-xx:/opt/mmomarket/
```

#### 4.2 Update Application Properties
```bash
nano src/main/resources/application.properties
```

Cập nhật database config:
```properties
# Database
spring.datasource.url=jdbc:mysql://your-rds-endpoint.rds.amazonaws.com:3306/mmomarket
spring.datasource.username=admin
spring.datasource.password=your-rds-password

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false

# Encryption Migration (DISABLE for initial deployment)
migration.encrypt-accounts=false

# Server
server.port=8080
```

#### 4.3 Build Application
```bash
cd /opt/mmomarket
mvn clean package -DskipTests

# JAR file sẽ được tạo tại:
# target/mmomarket-0.0.1-SNAPSHOT.jar
```

#### 4.4 Run Application
```bash
# Test run (foreground)
java -jar target/mmomarket-0.0.1-SNAPSHOT.jar

# Nếu chạy OK, stop bằng Ctrl+C
```

### BƯỚC 5: Setup SystemD Service (Production)

#### 5.1 Tạo Service File
```bash
sudo nano /etc/systemd/system/mmomarket.service
```

Nội dung file:
```ini
[Unit]
Description=MMOMarket Application
After=syslog.target network.target

[Service]
User=ubuntu
Group=ubuntu
WorkingDirectory=/opt/mmomarket
ExecStart=/usr/bin/java -jar /opt/mmomarket/target/mmomarket-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

# Environment Variables
Environment="ENCRYPTION_KEY=qR9tV4pB7yD0CqyM5oS1rN9tV4pB7yD0CqyM5oS1rN=="

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mmomarket

[Install]
WantedBy=multi-user.target
```

⚠️ **Lưu ý**: Thay `ubuntu` bằng user của bạn nếu dùng Amazon Linux (`ec2-user`)

#### 5.2 Enable và Start Service
```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable auto-start on boot
sudo systemctl enable mmomarket

# Start service
sudo systemctl start mmomarket

# Check status
sudo systemctl status mmomarket
```

**Output mong đợi:**
```
● mmomarket.service - MMOMarket Application
   Loaded: loaded (/etc/systemd/system/mmomarket.service; enabled)
   Active: active (running) since Thu 2024-11-07 10:30:00 UTC
   ...
```

#### 5.3 View Logs
```bash
# Real-time logs
sudo journalctl -u mmomarket -f

# Last 100 lines
sudo journalctl -u mmomarket -n 100

# Logs since boot
sudo journalctl -u mmomarket -b
```

### BƯỚC 6: Setup Nginx Reverse Proxy (Optional but Recommended)

#### 6.1 Install Nginx
```bash
# Ubuntu
sudo apt install nginx -y

# Amazon Linux 2
sudo amazon-linux-extras install nginx1 -y
```

#### 6.2 Configure Nginx
```bash
sudo nano /etc/nginx/sites-available/mmomarket
```

Nội dung:
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### 6.3 Enable Site
```bash
# Ubuntu
sudo ln -s /etc/nginx/sites-available/mmomarket /etc/nginx/sites-enabled/

# Test config
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
```

### BƯỚC 7: Setup SSL với Let's Encrypt (Highly Recommended)

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Get certificate
sudo certbot --nginx -d your-domain.com

# Auto-renewal is configured automatically
# Verify auto-renewal
sudo certbot renew --dry-run
```

---

# 3. MIGRATION DỮ LIỆU

## 🔄 Migration Trên Production Server

### BƯỚC 1: Backup Database
```bash
# SSH vào EC2
ssh -i "your-key.pem" ubuntu@ec2-xx-xx-xx-xx.compute.amazonaws.com

# Backup database
mysqldump -h your-rds-endpoint.rds.amazonaws.com -u admin -p mmomarket > backup_$(date +%Y%m%d_%H%M%S).sql

# Compress backup
gzip backup_*.sql
```

### BƯỚC 2: Enable Migration
```bash
# Stop application
sudo systemctl stop mmomarket

# Edit config
nano /opt/mmomarket/src/main/resources/application.properties
```

Thay đổi:
```properties
migration.encrypt-accounts=true
```

Rebuild:
```bash
cd /opt/mmomarket
mvn clean package -DskipTests
```

### BƯỚC 3: Run Migration
```bash
# Start application
sudo systemctl start mmomarket

# Monitor logs
sudo journalctl -u mmomarket -f
```

**Chờ đến khi thấy:**
```
========================================
MIGRATION COMPLETED SUCCESSFULLY
========================================
Total accounts processed: 5000
Total encrypted: 5000
Total already encrypted (skipped): 0
Total errors: 0
========================================
```

### BƯỚC 4: Disable Migration
```bash
# Stop application
sudo systemctl stop mmomarket

# Edit config
nano /opt/mmomarket/src/main/resources/application.properties
```

Thay đổi:
```properties
migration.encrypt-accounts=false
```

Rebuild và restart:
```bash
cd /opt/mmomarket
mvn clean package -DskipTests
sudo systemctl start mmomarket
```

### BƯỚC 5: Verify
```bash
# Check logs
sudo journalctl -u mmomarket -n 50

# Test application
curl http://localhost:8080/actuator/health
```

## ⏱️ Migration Time Estimates

| Số Accounts | Thời Gian Ước Tính |
|-------------|-------------------|
| 1,000 | ~10 giây |
| 10,000 | ~1.5 phút |
| 100,000 | ~15 phút |
| 1,000,000 | ~2.5 giờ |

---

# 4. TROUBLESHOOTING

## 🐛 Common Issues & Solutions

### Issue 1: "Decryption failed" Error

**Triệu chứng:**
```
RuntimeException: Decryption failed - data may be corrupted or key is incorrect
```

**Nguyên nhân:**
- Environment variable `ENCRYPTION_KEY` không được set
- Key sai
- Key bị thay đổi sau khi data đã mã hóa

**Giải pháp:**
```bash
# Kiểm tra env var
echo $ENCRYPTION_KEY

# Nếu empty, set lại
export ENCRYPTION_KEY="your-key-here"

# Restart application
sudo systemctl restart mmomarket

# Kiểm tra logs
sudo journalctl -u mmomarket -f
```

### Issue 2: Application Không Start

**Triệu chứng:**
```
Application failed to start
```

**Giải pháp:**
```bash
# Check logs
sudo journalctl -u mmomarket -n 100

# Common fixes:
# 1. Port already in use
sudo lsof -i :8080
sudo kill -9 <PID>

# 2. Database connection
# Check RDS security group allows EC2 IP

# 3. Rebuild
cd /opt/mmomarket
mvn clean package -DskipTests
sudo systemctl restart mmomarket
```

### Issue 3: Migration Shows Errors

**Triệu chứng:**
```
Progress: 100 processed, 95 encrypted, 0 skipped, 5 errors
```

**Giải pháp:**
```bash
# Check application logs for specific account IDs
sudo journalctl -u mmomarket | grep "ERROR encrypting account"

# Fix problematic accounts in database
mysql -h your-rds-endpoint -u admin -p

USE mmomarket;
SELECT id, accountData FROM ProductVariantAccounts WHERE id IN (123, 456, 789);

# Delete or fix invalid accounts
DELETE FROM ProductVariantAccounts WHERE id = 123 AND accountData IS NULL;

# Re-run migration
```

### Issue 4: Performance Degradation

**Triệu chứng:**
- Slow response times after enabling encryption

**Giải pháp:**
```bash
# Check CPU/Memory
top
htop

# Increase heap size
sudo nano /etc/systemd/system/mmomarket.service

# Add to ExecStart:
ExecStart=/usr/bin/java -Xmx2g -Xms1g -jar /opt/mmomarket/target/mmomarket-0.0.1-SNAPSHOT.jar

# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart mmomarket
```

### Issue 5: SSH Connection Lost During Migration

**Giải pháp:**
```bash
# Use screen or tmux
sudo apt install screen -y

# Start screen session
screen -S migration

# Run migration
sudo systemctl restart mmomarket
sudo journalctl -u mmomarket -f

# Detach: Ctrl+A, then D
# Reattach: screen -r migration
```

---

# 5. SECURITY BEST PRACTICES

## 🔒 Production Security Checklist

### Application Level
- [ ] ✅ `ENCRYPTION_KEY` set via environment variable (NOT in code)
- [ ] ✅ HTTPS enabled with valid SSL certificate
- [ ] ✅ Database connection uses SSL
- [ ] ✅ Application runs as non-root user
- [ ] ✅ CORS configured properly
- [ ] ✅ CSRF protection enabled

### Server Level
- [ ] ✅ EC2 security group allows only necessary ports
- [ ] ✅ SSH key-based authentication only (no password)
- [ ] ✅ Firewall configured (ufw or iptables)
- [ ] ✅ Regular security updates
- [ ] ✅ Fail2ban installed for brute-force protection

### Database Level
- [ ] ✅ RDS not publicly accessible
- [ ] ✅ Security group allows only EC2 IP
- [ ] ✅ Strong database password
- [ ] ✅ Automated backups enabled
- [ ] ✅ Encryption at rest enabled

### Monitoring & Backup
- [ ] ✅ CloudWatch alarms configured
- [ ] ✅ Application logs centralized
- [ ] ✅ Daily database backups
- [ ] ✅ Encryption key backed up separately
- [ ] ✅ Recovery plan documented

## 🔑 Key Management Best Practices

### 1. Key Storage
```bash
# AWS Systems Manager Parameter Store (Recommended)
aws ssm put-parameter \
  --name "/mmomarket/production/encryption-key" \
  --value "your-key-here" \
  --type "SecureString" \
  --description "Production encryption key"

# Retrieve in application
aws ssm get-parameter \
  --name "/mmomarket/production/encryption-key" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text
```

### 2. Key Rotation (Annual)
```bash
# 1. Generate new key
java com.mmo.util.EncryptionUtil

# 2. Keep old key temporarily
export OLD_ENCRYPTION_KEY="old-key-here"
export ENCRYPTION_KEY="new-key-here"

# 3. Deploy re-encryption script (future enhancement)
# 4. Remove old key after verification
```

### 3. Access Control
```bash
# Limit who can view environment variables
sudo chmod 600 /etc/environment

# Audit access
sudo ausearch -k encryption-key
```

---

# 📊 MONITORING & MAINTENANCE

## CloudWatch Metrics

### Setup Custom Metrics
```java
// In application
@Autowired
private MeterRegistry meterRegistry;

public void recordEncryption() {
    meterRegistry.counter("encryption.operations", "type", "encrypt").increment();
}

public void recordDecryption() {
    meterRegistry.counter("encryption.operations", "type", "decrypt").increment();
}
```

### Create Alarms
```bash
# High error rate
aws cloudwatch put-metric-alarm \
  --alarm-name "encryption-high-errors" \
  --alarm-description "High encryption error rate" \
  --metric-name "encryption.errors" \
  --namespace "MMOMarket" \
  --statistic "Sum" \
  --period 300 \
  --threshold 10 \
  --comparison-operator "GreaterThanThreshold"
```

## Regular Maintenance Tasks

### Daily
- [ ] Check application logs for errors
- [ ] Monitor response times
- [ ] Verify automatic backups completed

### Weekly
- [ ] Review security group rules
- [ ] Check disk space usage
- [ ] Update OS security patches

### Monthly
- [ ] Test backup restoration
- [ ] Review access logs
- [ ] Performance optimization

### Annually
- [ ] Rotate encryption key
- [ ] Security audit
- [ ] Disaster recovery drill

---

# 🎯 DEPLOYMENT CHECKLIST

## Pre-Deployment
- [ ] Code reviewed and tested locally
- [ ] Database migration plan ready
- [ ] Encryption key generated and backed up
- [ ] AWS resources provisioned
- [ ] DNS configured (if applicable)

## Deployment
- [ ] SSH into EC2
- [ ] Set `ENCRYPTION_KEY` environment variable
- [ ] Deploy application code
- [ ] Update application.properties
- [ ] Build application
- [ ] Setup systemd service
- [ ] Configure Nginx (optional)
- [ ] Setup SSL certificate
- [ ] Test application endpoints

## Post-Deployment
- [ ] Run migration (if needed)
- [ ] Verify encryption in database
- [ ] Test user flows (import, purchase, view)
- [ ] Monitor logs for errors
- [ ] Setup monitoring alerts
- [ ] Document configuration
- [ ] Notify team

---

# 📞 SUPPORT & RESOURCES

## Documentation Files
- `LOCAL_DEVELOPMENT_GUIDE.md` - Chi tiết chạy local
- `ENCRYPTION_README.md` - Technical documentation
- `ENCRYPTION_QUICK_REFERENCE.md` - Quick reference card
- `ENCRYPTION_IMPLEMENTATION_SUMMARY.md` - Implementation overview

## Useful Commands

### Local Development
```bash
# Run app
mvn spring-boot:run

# Test encryption
java com.mmo.util.EncryptionUtil

# Check database
mysql -u root -p mmomarket
SELECT id, LEFT(accountData, 50) FROM ProductVariantAccounts LIMIT 5;
```

### Production
```bash
# Service management
sudo systemctl {start|stop|restart|status} mmomarket

# Logs
sudo journalctl -u mmomarket -f

# Check environment
echo $ENCRYPTION_KEY

# Application health
curl http://localhost:8080/actuator/health
```

## Contact

For issues or questions:
1. Check logs first
2. Review troubleshooting section
3. Search documentation
4. Contact DevOps team

---

**Last Updated**: November 7, 2024  
**Version**: 1.0.0  
**Status**: ✅ Production Ready

🎉 **Chúc bạn deploy thành công!**

