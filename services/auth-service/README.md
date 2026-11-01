# auth-service

Đăng ký, đăng nhập, phát JWT (RS256), công bố khóa công khai qua JWKS.

| | |
|---|---|
| Cổng | 8081 |
| Database | `auth_db` |
| API | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /.well-known/jwks.json` |
| Cấu hình | `app.auth.issuer`, `app.auth.access-token-ttl`, biến môi trường `AUTH_RSA_PRIVATE_KEY` |

Request mẫu: `docs/api/auth.http` ở thư mục gốc repo.

## Khóa ký JWT

- Không đặt `AUTH_RSA_PRIVATE_KEY`: service sinh khóa tạm khi khởi động (chỉ dùng khi dev, token mất hiệu lực khi restart).
- Có đặt: PEM PKCS#8 (`-----BEGIN PRIVATE KEY-----`). Khi chạy nhiều instance phải dùng **cùng một khóa**.

Sinh khóa:

    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out auth-private.pem

Không commit file `.pem` vào Git.