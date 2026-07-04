# MEHNAT Phase 1 — Build Notes

All Phase 1 APIs from `ARCHITECTURE.md` are implemented in the existing Spring Boot backend under
`src/main/java/com/bsn/backend/social/` (89 files: 26 models, 25 repositories, 15 services, 12 controllers, 6 config, 5 common).
Existing agent-marketplace code is untouched; `/api/users/**` and `/api/agents/**` behave exactly as before.

## How to run

```bash
./gradlew bootRun        # needs JDK 21 + your MongoDB (Atlas URI already configured)
```

Swagger UI: `http://localhost:8080/swagger-ui.html` — all MEHNAT endpoints are grouped under `mehnat · *` tags.

**Prod env vars:** `JWT_SECRET` (required — dev fallback is insecure), `MEDIA_UPLOAD_BASE_URL`, `MEDIA_CDN_BASE_URL`.

## What was added to existing files

| File | Change |
|---|---|
| `model/User.java` | added `handle` field |
| `repository/UserRepository.java` | `findByHandle`, `existsByHandle` |
| `config/SecurityConfig.java` | JWT filter; `/api/v1/**` requires auth, `/api/v1/auth/**` open; legacy routes unchanged |
| `application.properties` | `mehnat.*` properties |

## Smoke-test the golden path

```bash
B=http://localhost:8080/api/v1

# 1. register → tokens
TOKEN=$(curl -s $B/auth/register -H 'Content-Type: application/json' -d '{
  "handle":"rohit_lifts","email":"rohit@test.io","password":"password123",
  "fullName":"Rohit","city":"Chamba","interests":["gym"]}' | jq -r .accessToken)
A="Authorization: Bearer $TOKEN"

# 2. upload session (stubbed presign) → complete
S=$(curl -s -X POST $B/media/upload-session -H "$A")
UP=$(echo $S | jq -r .uploadId); CT=$(echo $S | jq -r .captureToken)
curl -s -X POST $B/media/$UP/complete -H "$A" -H 'Content-Type: application/json' -d '{"durationSec":90}'

# 3. join challenge + create verifiable reel
curl -s -X POST $B/challenges/fitzone-30/join -H "$A"
CH=$(curl -s $B/challenges/fitzone-30 -H "$A" | jq -r .id)
POST=$(curl -s $B/posts -H "$A" -H 'Content-Type: application/json' -d "{
  \"type\":\"REEL\",\"uploadId\":\"$UP\",\"caption\":\"day 1\",\"tags\":[\"gym\"],
  \"challengeId\":\"$CH\",\"requestVerification\":true}" | jq -r .id)

# 4. verify → points + streak + RR + challenge credit + feed fan-out
curl -s $B/verifications -H "$A" -H 'Content-Type: application/json' -d "{
  \"postId\":\"$POST\",\"activityLabel\":\"gym\",\"effortSeconds\":3600,\"captureToken\":\"$CT\"}"

# 5. observe the ripple
curl -s $B/wallet -H "$A"          # +pts
curl -s $B/streaks/me -H "$A"      # streak 1, heatmap bit set
curl -s $B/ranks/me -H "$A"        # RR credited
curl -s $B/feed -H "$A"            # followers would see it (follow from a 2nd account)
curl -s $B/leaderboards/city/Chamba -h "$A"
```

## Seeded on first boot

Season `S1` (active, 90 days), challenge `fitzone-30` (back-loaded payout curve 5/15/44 pts per day, 2 freezes, 50-pt quit penalty, friend bonus 3→100), and 4 store items (2 REWARD aisle with KYC gate, 2 COSMETIC incl. an IMMORTAL-only theme). Critical unique indexes are created programmatically at startup.

## Phase 1 stubs to replace before launch

1. **Media presign** — `MediaService` returns stub URLs; swap `mehnat.media.*` for real S3 presigning + transcode pipeline.
2. **KYC** — `POST /users/me/kyc` auto-approves; wire a provider.
3. **Verification checks** — capture-token + duration heuristics; suspicious → manual queue at `GET /api/v1/admin/verifications`. Phase 2: liveness/face-match ML.
4. **Admin role** — `/api/v1/admin/**` currently only requires login; add a role check.
5. **Redis** — feeds/leaderboards run on indexed Mongo queries behind service interfaces; drop Redis in per `ARCHITECTURE.md` §2.3 without API changes.
