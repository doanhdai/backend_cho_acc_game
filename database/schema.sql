
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS account_skins;
DROP TABLE IF EXISTS skins;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS deposit_requests;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- BẢNG 1: users
-- Quản lý người dùng và ví tiền
-- =============================================
CREATE TABLE users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password      VARCHAR(255) NOT NULL,
  email         VARCHAR(100) NOT NULL UNIQUE,
  full_name     VARCHAR(100),
  avatar        VARCHAR(255),
  phone_zalo    VARCHAR(20)  NOT NULL,
  balance       DECIMAL(15,0) DEFAULT 0    COMMENT 'Tiền khả dụng',
  frozen_balance DECIMAL(15,0) DEFAULT 0   COMMENT 'Tiền đang bị khóa khi mua trung gian',
  role          ENUM('user', 'admin')  DEFAULT 'user',
  status        ENUM('active', 'banned') DEFAULT 'active',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- BẢNG 2: categories
-- Phân loại game (Liên Quân, Free Fire, PUBG…)
-- =============================================
CREATE TABLE categories (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  slug        VARCHAR(100) NOT NULL UNIQUE,
  image       VARCHAR(255),
  description TEXT
);

-- =============================================
-- BẢNG 3: accounts  (= game_accounts trong PRD)
-- Thông tin tin đăng bán acc
-- =============================================
CREATE TABLE accounts (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  seller_id        INT NOT NULL,
  category_id      INT NOT NULL,
  title            VARCHAR(255) NOT NULL,
  description      TEXT,
  price            DECIMAL(15,0) NOT NULL,
  original_price   DECIMAL(15,0)          COMMENT 'Giá gốc (để gạch ngang)',
  -- Thông tin game
  username         VARCHAR(100)           COMMENT 'Tên đăng nhập game - chỉ hiện khi mua xong',
  password         VARCHAR(100)           COMMENT 'Mật khẩu game - chỉ hiện khi mua xong',
  email_acc        VARCHAR(100)           COMMENT 'Email liên kết game (nếu có)',
  email_pass       VARCHAR(100)           COMMENT 'Mật khẩu email game (nếu có)',
  server           VARCHAR(50),
  level            INT,
  rank_level       VARCHAR(50)            COMMENT 'Hạng: Đồng, Bạc, Vàng... Thách Đấu',
  champions_count  INT DEFAULT 0,
  skins_count      INT DEFAULT 0,
  security_status  ENUM('TRANG_THONG_THIN', 'DINH_THONG_THIN') DEFAULT 'TRANG_THONG_THIN'
                   COMMENT 'TRANG=Trắng thông tin, DINH=Dính thông tin',
  -- Trạng thái
  status           ENUM('SHOWING', 'IN_TRANSACTION', 'SOLD', 'HIDDEN', 'PENDING_APPROVAL', 'REJECTED', 'DELETED') DEFAULT 'PENDING_APPROVAL'
                   COMMENT 'SHOWING=Đang rao, IN_TRANSACTION=Đang giao dịch, SOLD=Đã bán, HIDDEN=Ẩn, PENDING_APPROVAL=Chờ duyệt, REJECTED=Từ chối, DELETED=Đã xoá',
  images           JSON                   COMMENT 'Mảng URL ảnh',
  is_featured      BOOLEAN DEFAULT FALSE,
  view_count       INT DEFAULT 0,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (seller_id)   REFERENCES users(id)      ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE INDEX idx_accounts_status     ON accounts(status);
CREATE INDEX idx_accounts_price      ON accounts(price);
CREATE INDEX idx_accounts_seller     ON accounts(seller_id);
CREATE INDEX idx_accounts_category   ON accounts(category_id);

-- =============================================
-- BẢNG 4: skins
-- Từ điển toàn bộ skin trong game (Admin cấu hình)
-- =============================================
CREATE TABLE skins (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  champion_name VARCHAR(100) NOT NULL COMMENT 'Tên tướng: Valhein, Nakroth...',
  skin_name     VARCHAR(100) NOT NULL COMMENT 'Tên skin: Vũ điệu đón xuân...',
  image_url     VARCHAR(255) NOT NULL COMMENT 'Link ảnh skin trên S3/Cloudinary'
);

-- =============================================
-- BẢNG 5: account_skins
-- Bảng trung gian: Acc nào có Skin nào (để lọc)
-- =============================================
CREATE TABLE account_skins (
  account_id INT NOT NULL,
  skin_id    INT NOT NULL,
  PRIMARY KEY (account_id, skin_id),
  FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
  FOREIGN KEY (skin_id)    REFERENCES skins(id)    ON DELETE CASCADE
);

-- =============================================
-- BẢNG 6: orders
-- Giao dịch trung gian (escrow)
-- =============================================
CREATE TABLE orders (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  buyer_id      INT NOT NULL,
  seller_id     INT NOT NULL,
  account_id    INT NOT NULL,
  amount        DECIMAL(15,0) NOT NULL  COMMENT 'Giá trị giao dịch = giá acc',
  fee           DECIMAL(15,0) DEFAULT 0 COMMENT 'Phí sàn 3% giữ lại',
  status        ENUM('PENDING', 'COMPLETED', 'CANCELLED') DEFAULT 'PENDING',
  cancel_reason VARCHAR(500),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (buyer_id)   REFERENCES users(id)    ON DELETE CASCADE,
  FOREIGN KEY (seller_id)  REFERENCES users(id)    ON DELETE CASCADE,
  FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_orders_buyer  ON orders(buyer_id);
CREATE INDEX idx_orders_seller ON orders(seller_id);
CREATE INDEX idx_orders_status ON orders(status);

-- =============================================
-- BẢNG 7: messages
-- Phòng chat 3 bên: Người mua, Người bán, Admin
-- =============================================
CREATE TABLE messages (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  order_id   INT  NOT NULL,
  sender_id  INT  NOT NULL,
  message    TEXT NOT NULL,
  is_private BOOLEAN DEFAULT FALSE COMMENT 'TRUE = chỉ Admin nhìn thấy',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id)  REFERENCES orders(id) ON DELETE CASCADE,
  FOREIGN KEY (sender_id) REFERENCES users(id)  ON DELETE CASCADE
);

CREATE INDEX idx_messages_order ON messages(order_id);

-- =============================================
-- BẢNG 8: transactions
-- Lịch sử biến động số dư ví tiền
-- =============================================
CREATE TABLE transactions (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  user_id        INT NOT NULL,
  amount         DECIMAL(15,0) NOT NULL,
  type           ENUM(
                   'DEPOSIT',           -- Nạp tiền vào ví
                   'POST_FEE',          -- Phí đăng tin 5.000đ
                   'MIDDLEMAN_HOLD',    -- Đóng băng khi mua trung gian
                   'MIDDLEMAN_RELEASE', -- Giải ngân cho người bán
                   'REFUND'            -- Hoàn tiền khi hủy đơn
                 ) NOT NULL,
  balance_before DECIMAL(15,0) NOT NULL,
  balance_after  DECIMAL(15,0) NOT NULL,
  description    TEXT,
  reference_id   INT            COMMENT 'ID của order hoặc deposit_request liên quan',
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_tx_user ON transactions(user_id);
CREATE INDEX idx_tx_type ON transactions(type);

-- =============================================
-- BẢNG 9: deposit_requests
-- Yêu cầu nạp tiền (manual + auto webhook)
-- =============================================
CREATE TABLE deposit_requests (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  user_id         INT NOT NULL,
  amount          DECIMAL(15,0) NOT NULL,
  method          VARCHAR(50) DEFAULT 'bank_transfer' COMMENT 'bank_transfer | momo | zalopay',
  transaction_ref VARCHAR(100)  COMMENT 'Mã giao dịch ngân hàng',
  note            TEXT          COMMENT 'Nội dung chuyển khoản',
  status          ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
  admin_note      TEXT          COMMENT 'Ghi chú của admin khi duyệt/từ chối',
  processed_by    INT           COMMENT 'Admin đã xử lý',
  processed_at    TIMESTAMP NULL,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)      REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_deposit_user   ON deposit_requests(user_id);
CREATE INDEX idx_deposit_status ON deposit_requests(status);

-- =============================================
-- DỮ LIỆU MẪU (SEED DATA)
-- Mật khẩu 'admin123' hash BCrypt (tương thích Spring Security)
-- =============================================
INSERT INTO users (username, password, email, full_name, phone_zalo, balance, role, status) VALUES
('admin',    '$2a$10$uq797VdziNjJfCvRFs9O0.VHCPiDXxKlSICCLTjVha..S0crHSjXm', 'admin@shopaccgame.vn',   'Admin Shop',     '0999999999', 10000000, 'admin', 'active'),
('user01',   '$2a$10$uq797VdziNjJfCvRFs9O0.VHCPiDXxKlSICCLTjVha..S0crHSjXm', 'user01@gmail.com',       'Nguyễn Văn A',   '0912345678', 500000,   'user',  'active'),
('seller01', '$2a$10$uq797VdziNjJfCvRFs9O0.VHCPiDXxKlSICCLTjVha..S0crHSjXm', 'seller01@gmail.com',     'Trần Văn B',     '0987654321', 100000,   'user',  'active');

-- Danh mục game
INSERT INTO categories (name, slug, image, description) VALUES
('Liên Quân Mobile', 'lien-quan-mobile', '/lienquan.png',  'Mua bán acc Liên Quân Mobile uy tín, giá rẻ.'),
('Free Fire',        'free-fire',        '/freefire.png',  'Shop acc Free Fire giá rẻ, an toàn.'),
('PUBG Mobile',      'pubg-mobile',      '/pubg.png',      'Acc PUBG Mobile cực phẩm, đa dạng.'),
('LMHT',             'lmht',             '/lmht.png',      'Tài khoản Liên Minh Huyền Thoại uy tín.');

-- Skin Liên Quân Mobile
INSERT INTO skins (champion_name, skin_name, image_url) VALUES
('Violet',   'Pháo hoa tuôn trào',   'https://placehold.co/400x300/e91e63/ffffff?text=Violet+PHTT'),
('Violet',   'Thứ Nguyên Vệ Thần',   'https://placehold.co/400x300/3f51b5/ffffff?text=Violet+TNVT'),
('Nakroth',  'Thứ Nguyên Vệ Thần',   'https://placehold.co/400x300/00bcd4/ffffff?text=Nakroth+TNVT'),
('Nakroth',  'Lôi Quang Sứ',         'https://placehold.co/400x300/ffeb3b/000000?text=Nakroth+LQS'),
('Valhein',  'Vũ điệu đón xuân',     'https://placehold.co/400x300/4caf50/ffffff?text=Valhein+VDDX'),
('Valhein',  'Mũi tên mặt trời',     'https://placehold.co/400x300/ff5722/ffffff?text=Valhein+MTMT');

-- Tài khoản mẫu
INSERT INTO accounts
  (seller_id, category_id, title, description, price, original_price, username, password, email_acc,
   rank_level, champions_count, skins_count, security_status, status, images, is_featured)
VALUES
(3, 1,
 'Acc Liên Quân Siêu Cực Phẩm - Nakroth TNVT + Violet PHTT',
 'Tài khoản tâm huyết. Đầy đủ skin VIP Thứ Nguyên Vệ Thần của Nakroth và Violet Pháo hoa tuôn trào.',
 650000, 800000, 'lq_sieucaptraitim', 'passgarena123', 'sieucap@gmail.com',
 'Cao Thủ', 95, 142, 'TRANG_THONG_THIN', 'SHOWING',
 '["https://placehold.co/800x450/13132a/6c63ff?text=Nakroth+TNVT","https://placehold.co/800x450/13132a/e91e63?text=Violet+PHTT"]',
 TRUE),

(3, 1,
 'Acc Liên Quân giá rẻ học sinh - Valhein Vũ điệu đón xuân',
 'Phù hợp cho anh em cày cuốc, rank Kim Cương. Đã dính thông tin cũ.',
 150000, 200000, 'lq_hocsinh2004', 'passgarena456', NULL,
 'Kim Cương', 45, 32, 'DINH_THONG_THIN', 'SHOWING',
 '["https://placehold.co/800x450/13132a/4caf50?text=Valhein+VDDX"]',
 FALSE),

(2, 2,
 'Acc Free Fire VIP full MP40 Mãng Xà',
 'Cực phẩm Free Fire rank Tinh Anh, full súng VIP.',
 450000, 500000, 'ff_sieucapvip', 'ffpass123', NULL,
 'Tinh Anh', 40, 85, 'TRANG_THONG_THIN', 'SHOWING',
 '[]',
 TRUE);

-- Gắn skin cho tài khoản mẫu
INSERT INTO account_skins (account_id, skin_id) VALUES (1,1),(1,2),(1,3),(1,4),(2,5);

-- =============================================
-- BẢNG 10: system_settings
-- Quản lý cấu hình toàn hệ thống
-- =============================================
CREATE TABLE IF NOT EXISTS system_settings (
  setting_key   VARCHAR(100) PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL
);

INSERT INTO system_settings (setting_key, setting_value) VALUES ('post_fee_percent', '1.0');
