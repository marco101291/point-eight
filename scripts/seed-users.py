#!/usr/bin/env python3
"""Seeds java-system with synthetic users via POST /api/users.

Registration alone never assigns anyone a match — AssignNextMatchUseCase only fires reactively
off MatchExpiredEvent, so it needs an existing match to react to. A handful of hand-typed fixture
users made that easy to miss; this script exists so RandomEligibleCandidateStrategy actually has a
real pool to pick from once matches start getting created (manually, or once the still-missing
"assign a first match" trigger exists — see docs/architecture.md's open questions).

Layer 1 + Layer 2 are both randomized directly (no relation to the sign-up questionnaire's
mapping — this script talks straight to the API, it doesn't simulate answering nine questions
thousands of times). Uses only the standard library on purpose: this is a one-off seeding tool,
not something that should need a pip install to run.

Usage:
    python3 scripts/seed-users.py [count] [--base-url http://localhost:8080] [--workers 16]
"""

import argparse
import concurrent.futures
import json
import random
import secrets
import urllib.error
import urllib.request

CITIES = [
    "Ciudad de México", "Guadalajara", "Monterrey", "Puebla", "Querétaro",
    "Bogotá", "Medellín", "Lima", "Santiago", "Buenos Aires", "Rosario",
    "Córdoba", "Montevideo", "Quito", "San José", "Panamá",
]

PROFESSIONS = [
    "medico", "enfermera", "abogado", "policia", "bombero", "periodista", "trader", "piloto",
    "bibliotecaria", "archivista", "jardinero", "ceramista", "traductor", "docente", "contador",
    "programador", "disenadora", "chef", "musico", "arquitecta", "fotografo", "veterinaria",
]

HOBBIES = [
    "cine", "lectura", "senderismo", "ciclismo", "cocina", "fotografia", "pintura", "musica",
    "yoga", "natacion", "ajedrez", "jardineria", "escalada", "teatro", "baile", "ceramica",
]

GENDERS = ["FEMALE", "MALE", "NON_BINARY"]
SEEKING_TYPES = ["CASUAL", "SHORT_TERM", "LONG_TERM", "UNDEFINED"]
ATTACHMENT_STYLES = ["SECURE", "ANXIOUS", "AVOIDANT", "DISORGANIZED"]


def random_user(run_id: str, i: int) -> dict:
    return {
        "email": f"seed-{run_id}-{i:05d}@example.com",
        "password": "password123",
        "age": random.randint(18, 65),
        "gender": random.choice(GENDERS),
        "seekingGenders": random.sample(GENDERS, k=random.randint(1, len(GENDERS))),
        "seekingType": random.choice(SEEKING_TYPES),
        "city": random.choice(CITIES),
        "profession": random.choice(PROFESSIONS),
        "hobbies": random.sample(HOBBIES, k=random.randint(1, 4)),
        "photoUrl": f"https://picsum.photos/seed/seed-{run_id}-{i}/900/1400",
        "attachmentStyle": random.choice(ATTACHMENT_STYLES),
        "attachmentIntensity": round(random.uniform(0.1, 0.9), 2),
        "communicationProfile": {
            "criticism": round(random.uniform(0.0, 1.0), 2),
            "contempt": round(random.uniform(0.0, 1.0), 2),
            "defensiveness": round(random.uniform(0.0, 1.0), 2),
            "stonewalling": round(random.uniform(0.0, 1.0), 2),
        },
    }


def register(base_url: str, payload: dict) -> int:
    body = json.dumps(payload).encode()
    request = urllib.request.Request(
        f"{base_url}/api/users",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("count", type=int, nargs="?", default=3000)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--workers", type=int, default=16)
    args = parser.parse_args()

    run_id = secrets.token_hex(3)
    succeeded, failed = 0, 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [
            pool.submit(register, args.base_url, random_user(run_id, i))
            for i in range(args.count)
        ]
        for done, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            if future.result() == 201:
                succeeded += 1
            else:
                failed += 1
            if done % 200 == 0 or done == args.count:
                print(f"{done}/{args.count} ({succeeded} created, {failed} failed)")

    print(f"Done — run {run_id}: {succeeded} created, {failed} failed")


if __name__ == "__main__":
    main()
