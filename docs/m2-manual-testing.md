# M2 — Manual testing guide

Minimal engine: FastAPI returns a random score + expiry; Java consumes it via synchronous REST.
This guide tests the happy path and the two error paths that matter (nonexistent match, Engine
down). Corresponds to branch `feat/m2-minimal-engine`.

## 0. Automated, before touching anything by hand

```bash
./scripts/java-test.sh          # 73 tests (63 from M1 + 10 new in M2)
```

The Python side needs a local venv (there's no test container for the Engine yet):

```bash
cd python-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
pytest -q                       # 3 new tests in tests/test_compatibility.py
mypy app                        # strict, should come out clean
black --check app tests         # clean except app/config.py:13 (pre-existing debt from M0/M1)
```

## 1. Bring everything up

```bash
cp -n .env.example .env
docker compose up -d --build
```

Wait for `pointeight-system` to be `healthy`:

```bash
docker inspect -f '{{.State.Health.Status}}' pointeight-system
```

## 2. The Engine is online and already knows about M2

```bash
curl -s http://localhost:8000/api/status | python3 -m json.tool
```

Expected: `"milestone": "M2"`.

## 3. Happy path: two users → a match → a score

```bash
UA=$(curl -sf -X POST http://localhost:8080/api/users -H 'Content-Type: application/json' -d '{
  "age": 28, "gender": "FEMALE", "seekingGenders": ["MALE"], "seekingType": "LONG_TERM",
  "city": "Buenos Aires", "profession": "arquitecta", "hobbies": ["cine"],
  "attachmentStyle": "SECURE", "attachmentIntensity": 0.3,
  "communicationProfile": {"criticism":0.2,"contempt":0.1,"defensiveness":0.2,"stonewalling":0.1},
  "infidelityHistory": false, "relationshipHistory": 1, "activeAddiction": false
}')
UAID=$(echo "$UA" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

UB=$(curl -sf -X POST http://localhost:8080/api/users -H 'Content-Type: application/json' -d '{
  "age": 31, "gender": "MALE", "seekingGenders": ["FEMALE"], "seekingType": "LONG_TERM",
  "city": "Buenos Aires", "profession": "docente", "hobbies": ["ajedrez"],
  "attachmentStyle": "ANXIOUS", "attachmentIntensity": 0.6,
  "communicationProfile": {"criticism":0.4,"contempt":0.3,"defensiveness":0.4,"stonewalling":0.3},
  "infidelityHistory": false, "relationshipHistory": 2, "activeAddiction": false
}')
UBID=$(echo "$UB" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

MATCH=$(curl -sf -X POST http://localhost:8080/api/matches -H 'Content-Type: application/json' \
  -d "{\"userAId\":\"$UAID\",\"userBId\":\"$UBID\"}")
MID=$(echo "$MATCH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "$MATCH" | python3 -m json.tool     # compatibilityScore: still null

curl -sf -X POST "http://localhost:8080/api/matches/$MID/score" | python3 -m json.tool
```

**Verify:**
- `compatibilityScore` is a number between 0.0 and 1.0 (it used to be `null`).
- `GET /api/matches/$MID` returns the same score — it was persisted, not just a value in the
  response.

```bash
curl -s "http://localhost:8080/api/matches/$MID" | python3 -m json.tool
```

## 4. Layer 2 doesn't leak anywhere

```bash
echo "$UA" | grep -iE "attachment|criticism|contempt|defensiveness|stonewalling|infidelity|addiction|stressBaseline|commitmentPace" && echo "FAILED: Layer 2 leaked" || echo "OK: doesn't appear"
```

This is the same thing `UserControllerTest` already covers, but it's worth watching it pass over
the real network at least once: Layer 2 only traveled inside the payload Java builds for the
Engine (`AgentSnapshot` → `EngineCompatibilityClient`), never in an HTTP response.

## 5. Error path 1 — nonexistent match

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  "http://localhost:8080/api/matches/$(python3 -c 'import uuid;print(uuid.uuid4())')/score"
```

Expected: `404`.

## 6. Error path 2 — the Engine is down

```bash
docker stop pointeight-engine

# using the same $MID from above, or creating a new match with another user
curl -s -w "\nHTTP:%{http_code}\n" -X POST "http://localhost:8080/api/matches/$MID/score"
```

Expected: `502` with `type: urn:pointeight:engine-unavailable`. The match can't already have a
score assigned if you want to repeat this test (`assignCompatibilityScore` doesn't revalidate over
a terminal match, but if it already has a prior score it simply overwrites it once the Engine
responds again).

```bash
docker start pointeight-engine
```

## 7. Cleanup

```bash
docker compose down -v
```

## What this guide does NOT cover (out of scope for M2)

- The score doesn't get requested on its own again: today it's a manual `POST`. In M4
  `MatchAssignedEvent` triggers it.
- The `expiryDays` the Engine returns is received but not applied to `Match.expiryDuration` (that
  field is immutable and Java sets it when the match is created) — see DEC-010 in
  `docs/architecture.md`.
- The score is random (`random.random()` in Python); there's no Monte Carlo or Markov yet. That's
  M3.
