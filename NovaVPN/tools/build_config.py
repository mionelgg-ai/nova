#!/usr/bin/env python3
"""
Build servers.json (the file the app downloads) from servers.source.json.

    pip install cryptography
    python build_config.py servers.source.json servers.json

Then upload servers.json to GitHub (or any static host) and put its raw URL
in app/src/main/java/app/nova/vpn/Cfg.kt  ->  CONFIG_URLS

What gets encrypted:
  * free servers     -> AES-GCM, key derived from "appSecret" (obfuscation: stops casual copying)
  * premium servers  -> AES-GCM, key = random premium key
  * each activation code stores the premium key wrapped with a key derived from THAT code,
    so only someone who knows a valid code can read premium links.
Remove a code from the source and rebuild to revoke it (apps pick it up on next refresh).
"""
import base64, hashlib, json, os, sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

FREE_ITER = 10_000   # must match Crypto.kt
CODE_ITER = 60_000   # must match Crypto.kt


def b64(b: bytes) -> str:
    return base64.b64encode(b).decode()


def pbkdf2(password: str, salt: bytes, iters: int) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iters, 32)


def seal(key: bytes, plaintext: bytes) -> str:
    iv = os.urandom(12)
    return b64(iv + AESGCM(key).encrypt(iv, plaintext, None))


def normalize(code: str) -> str:
    return code.strip().upper().replace(" ", "")


def code_id(code: str) -> str:
    return hashlib.sha256(normalize(code).encode()).hexdigest()[:8]


def main(src_path: str, out_path: str) -> None:
    src = json.load(open(src_path, encoding="utf-8"))
    secret = src["appSecret"]
    if secret == "CHANGE_ME_TO_RANDOM_STRING":
        print("WARNING: appSecret is still the default. Change it here AND in Cfg.kt.")

    salt = os.urandom(16)
    free_key = pbkdf2(secret, salt, FREE_ITER)
    premium_key = os.urandom(32)

    servers = []
    seen = set()
    for s in src["servers"]:
        if s["id"] in seen:
            sys.exit(f"duplicate server id: {s['id']}")
        seen.add(s["id"])
        premium = bool(s.get("premium", False))
        key = premium_key if premium else free_key
        servers.append({
            "id": s["id"],
            "name": s["name"],
            "nameFa": s.get("nameFa", ""),
            "cc": s["cc"].upper(),
            "city": s.get("city", ""),
            "premium": premium,
            "link": seal(key, s["link"].strip().encode()),
        })

    codes = []
    for c in src.get("codes", []):
        code = normalize(c["code"])
        codes.append({
            "id": code_id(code),
            "wrapped": seal(pbkdf2(code, salt, CODE_ITER), premium_key),
            "expires": c.get("expires", ""),
        })

    out = {
        "v": 1,
        "salt": b64(salt),
        "telegram": src["telegram"],
        "servers": servers,
        "codes": codes,
    }
    if src.get("offer"):
        out["offer"] = src["offer"]

    json.dump(out, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"OK  {len(servers)} servers ({sum(1 for s in servers if not s['premium'])} free), "
          f"{len(codes)} codes -> {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: build_config.py servers.source.json servers.json")
    main(sys.argv[1], sys.argv[2])
