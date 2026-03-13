flowchart LR
    subgraph REGISTER["Đăng Ký"]
        R1[POST /auth/register] --> R2[POST /auth/verify-otp]
        R2 --> R3[POST /auth/login]
    end

    subgraph SESSION["Quản Lý Phiên"]
        S1[POST /auth/refresh] -->|"accessToken mới"| S2["Tiếp tục dùng app"]
        S3[POST /auth/logout] -->|"Revoke refresh token"| S4["Đăng xuất"]
    end

    subgraph PASSWORD["Đặt Lại Mật Khẩu"]
        P1[POST /auth/forgot-password] -->|"Gửi link email"| P2[POST /auth/reset-password]
        P3[POST /auth/change-password] -->|"Đổi khi đã đăng nhập"| P4["Revoke tất cả refresh token"]
    end

    subgraph PROFILE["Hồ Sơ & Media"]
        PR1[PUT /users/me] 
        PR2[POST /users/me/avatar] -->|"R2 upload"| PR3["Max 2MB · JPEG/PNG/WEBP"]
        PR4[PUT /users/me/avatar/url] -->|"External URL"| PR5["https:// only"]
        PR6[POST /users/me/banner] -->|"R2 upload"| PR7["Max 3MB · JPEG/PNG/WEBP"]
        PR8[DELETE /users/me/avatar]
        PR9[DELETE /users/me/banner]
    end

    R3 --> SESSION
    R3 --> PASSWORD
    R3 --> PROFILE