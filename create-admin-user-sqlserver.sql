-- Script tạo tài khoản Admin và Driver cho SQL Server
-- Password cho tất cả tài khoản: "password123"
-- BCrypt hash của "password123": $2a$10$vHkJqN5JqW7q7o9hP7qBTe9vKP9sWmN4MzUqLqP8qZ9hP7qBTe9vK
USE EVDATABASE;
GO -- Xóa dữ liệu cũ (nếu có)
DELETE FROM nguoi_dung
WHERE email IN (
    'admin@evcharging.com',
    'driver1@gmail.com',
    'driver2@gmail.com',
    'staff1@evcharging.com'
  );
GO -- 1. Tạo tài khoản Admin
INSERT INTO nguoi_dung (
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    wallet_balance,
    created_at,
    updated_at
  )
VALUES (
    'admin@evcharging.com',
    '$2a$10$vHkJqN5JqW7q7o9hP7qBTe9vKP9sWmN4MzUqLqP8qZ9hP7qBTe9vK',
    'Admin',
    'System',
    '0123456789',
    'ADMIN',
    0.00,
    GETDATE(),
    GETDATE()
  );
GO -- 2. Tạo tài khoản Driver 1
INSERT INTO nguoi_dung (
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    wallet_balance,
    created_at,
    updated_at
  )
VALUES (
    'driver1@gmail.com',
    '$2a$10$vHkJqN5JqW7q7o9hP7qBTe9vKP9sWmN4MzUqLqP8qZ9hP7qBTe9vK',
    N 'Nguyễn Văn',
    N'An',
    '0987654321',
    'EV_DRIVER',
    500000.00,
    GETDATE(),
    GETDATE()
  );
GO -- 3. Tạo tài khoản Driver 2
INSERT INTO nguoi_dung (
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    wallet_balance,
    created_at,
    updated_at
  )
VALUES (
    'driver2@gmail.com',
    '$2a$10$vHkJqN5JqW7q7o9hP7qBTe9vKP9sWmN4MzUqLqP8qZ9hP7qBTe9vK',
    N'Trần Thị',
    N'Bình',
    '0965432187',
    'EV_DRIVER',
    250000.00,
    GETDATE(),
    GETDATE()
  );
GO -- 4. Tạo tài khoản Staff 1
INSERT INTO nguoi_dung (
    email,
    password,
    first_name,
    last_name,
    phone,
    role,
    wallet_balance,
    created_at,
    updated_at
  )
VALUES (
    'staff1@evcharging.com',
    '$2a$10$vHkJqN5JqW7q7o9hP7qBTe9vKP9sWmN4MzUqLqP8qZ9hP7qBTe9vK',
    N'Lê',
    N'Bảo',
    '0912345678',
    'CS_STAFF',
    0.00,
    GETDATE(),
    GETDATE()
  );
GO -- Kiểm tra kết quả
SELECT id,
  email,
  first_name,
  last_name,
  phone,
  role,
  wallet_balance,
  created_at
FROM nguoi_dung
WHERE email IN (
    'admin@evcharging.com',
    'driver1@gmail.com',
    'driver2@gmail.com',
    'staff1@evcharging.com'
  )
ORDER BY role,
  email;
GO