}

        @Test
        void healthEndpointIsPublic() throws Exception {
                assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        }

        // ----------------------------------------------------------------- tuần 3

        @Test
        void registrationWritesUserRegisteredEventToOutbox() throws Exception {
                HttpResponse<String> registered = post("/api/v1/auth/register",
                                registerBody("Outbox.Check@Example.com", "Passw0rd!2026", "GUEST"));
                String userId = extract(registered.body(), "userId");

                Map<String, Object> row = jdbc.queryForMap(
                                "SELECT topic, type, payload::text AS payload, published_at FROM outbox WHERE aggregate_id = ?",
                                UUID.fromString(userId));

                assertThat(row.get("topic")).isEqualTo("user.events");
                assertThat(row.get("type")).isEqualTo("UserRegistered");
                assertThat((String) row.get("payload")).contains("outbox.check@example.com").contains(userId);
                assertThat(row.get("published_at")).as("OutboxPublisher đang tắt nên chưa gửi").isNull();
        }

        @Test
        void failedRegistrationWritesNothingToOutbox() throws Exception {
                post("/api/v1/auth/register", registerBody("once@example.com", "Passw0rd!2026", "GUEST"));
                post("/api/v1/auth/register", registerBody("once@example.com", "Passw0rd!2026", "GUEST")); // trùng, bị
                                                                                                           // 409

                Integer events = jdbc.queryForObject(
                                "SELECT count(*) FROM outbox WHERE payload::text LIKE '%once@example.com%'",
                                Integer.class);
                assertThat(events).isEqualTo(1);
        }

        @Test
        void loginReturnsRefreshTokenAndRefreshRotatesIt() throws Exception {
                String loginBody = registerAndLogin("rotate@example.com");
                String refresh1 = extract(loginBody, "refreshToken");

                HttpResponse<String> refreshed = post("/api/v1/auth/refresh", refreshBody(refresh1));

                assertThat(refreshed.statusCode()).isEqualTo(200);
                String refresh2 = extract(refreshed.body(), "refreshToken");
                assertThat(refresh2).isNotEqualTo(refresh1);
                assertThat(decodeViaJwks(extract(refreshed.body(), "accessToken")).getSubject()).isNotBlank();

                // Trong DB chỉ có BĂM của refresh token, không có token gốc
                Integer rawStored = jdbc.queryForObject(
                                "SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class, refresh1);
                assertThat(rawStored).isZero();
        }

        @Test
        void reusingAnOldRefreshTokenRevokesTheWholeFamily() throws Exception {
                String loginBody = registerAndLogin("theft@example.com");
                String refresh1 = extract(loginBody, "refreshToken");
                String refresh2 = extract(post("/api/v1/auth/refresh", refreshBody(refresh1)).body(), "refreshToken");

                HttpResponse<String> reuse = post("/api/v1/auth/refresh", refreshBody(refresh1));
                assertThat(reuse.statusCode()).isEqualTo(401);
                assertThat(reuse.body()).contains("INVALID_REFRESH_TOKEN");

                // Token mới nhất cũng bị thu hồi vì nghi bị đánh cắp
                HttpResponse<String> newestToken = post("/api/v1/auth/refresh", refreshBody(refresh2));
                assertThat(newestToken.statusCode()).isEqualTo(401);
        }

        @Test
        void logoutRevokesRefreshToken() throws Exception {
                String refresh = extract(registerAndLogin("logout@example.com"), "refreshToken");

                HttpResponse<String> loggedOut = post("/api/v1/auth/logout", refreshBody(refresh));
                assertThat(loggedOut.statusCode()).isEqualTo(204);

                assertThat(post("/api/v1/auth/refresh", refreshBody(refresh)).statusCode()).isEqualTo(401);
                // Đăng xuất hai lần hay token lạ đều không lỗi
                assertThat(post("/api/v1/auth/logout", refreshBody(refresh)).statusCode()).isEqualTo(204);
                assertThat(post("/api/v1/auth/logout", refreshBody("token-la")).statusCode()).isEqualTo(204);
        }

        @Test
        void unknownRefreshTokenReturns401() throws Exception {
                HttpResponse<String> response = post("/api/v1/auth/refresh", refreshBody("khong-ton-tai"));
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("INVALID_REFRESH_TOKEN");
        }

        @Test
        void tooManyWrongPasswordsReturn429EvenForTheCorrectPassword() throws Exception {
                post("/api/v1/auth/register", registerBody("limit@example.com", "Passw0rd!2026", "GUEST"));

                for (int i = 0; i < 5; i++) {
                        assertThat(post("/api/v1/auth/login", loginBody("limit@example.com", "sai-mat-khau"))
                                        .statusCode())
                                        .isEqualTo(401);
                }

                HttpResponse<String> blocked = post("/api/v1/auth/login",
                                loginBody("limit@example.com", "sai-mat-khau"));
                assertThat(blocked.statusCode()).isEqualTo(429);
                assertThat(blocked.body()).contains("TOO_MANY_ATTEMPTS");
                assertThat(Long.parseLong(blocked.headers().firstValue("Retry-After").orElse("0"))).isPositive();

                HttpResponse<String> correctButBlocked = post("/api/v1/auth/login",
                                loginBody("limit@example.com", "Passw0rd!2026"));
                assertThat(correctButBlocked.statusCode()).isEqualTo(429);
        }

        @Test
        void successfulLoginResetsTheFailureCounter() throws Exception {
                post("/api/v1/auth/register", registerBody("reset@example.com", "Passw0rd!2026", "GUEST"));

                for (int i = 0; i < 4; i++) {
                        post("/api/v1/auth/login", loginBody("reset@example.com", "sai-mat-khau"));
                }
                assertThat(post("/api/v1/auth/login", loginBody("reset@example.com", "Passw0rd!2026")).statusCode())
                                .isEqualTo(200);

                // Bộ đếm đã về 0: lại được sai thêm 4 lần mà chưa bị khóa
                for (int i = 0; i < 4; i++) {
                        assertThat(post("/api/v1/auth/login", loginBody("reset@example.com", "sai-mat-khau"))
                                        .statusCode())
                                        .isEqualTo(401);
                }
        }
