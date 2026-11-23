#!/usr/bin/env python3
"""One-time, local Google Cloud Desktop OAuth flow for NOTHING Marketplace.
Requires only Python 3 stdlib; never uploads the client JSON or prints tokens.
Writes client ID, secret and refresh token only to the ignored backend/.env.
The Google OAuth consent is explicitly granted by the Gmail account owner.
"""
import base64
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import secrets
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import webbrowser

SCOPES = "https://www.googleapis.com/auth/gmail.send"
AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URL = "https://oauth2.googleapis.com/token"
DEFAULT_ENV = Path("backend/.env")

def form_request(url, fields):
    payload = urllib.parse.urlencode(fields).encode("utf-8")
    request = urllib.request.Request(
        url, data=payload, headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        # Never print the body, which may expose OAuth details.
        raise RuntimeError(f"Google OAuth request failed (HTTP {exc.code}). Check client settings and consent.") from None

def update_private_env(path, values):
    if not path.is_file():
        raise RuntimeError(f"Create {path} from backend/.env.example before running this script")
    # Preserve existing passwords and all non-OAuth configuration lines.
    lines = path.read_text(encoding="utf-8").splitlines()
    replaced = set()
    new_lines = []
    for line in lines:
        match = re.match(r"^([A-Z_][A-Z0-9_]*)=", line)
        key = match.group(1) if match else None
        if key in values:
            if key not in replaced:
                new_lines.append(key + "=" + values[key])
                replaced.add(key)
            continue
        new_lines.append(line)
    for key, val in values.items():
        if key not in replaced:
            new_lines.append(key + "=" + val)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Atomic replace + owner-only permissions, including previously existing .env.
    fd, temporary = tempfile.mkstemp(prefix=".gmail-env-", dir=path.parent)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            file.write("\n".join(new_lines) + "\n")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.remove(temporary)

def main():
    if len(sys.argv) not in (2, 3):
        print("Usage: python3 scripts/setup-gmail-oauth.py /path/to/client_secret.json [backend/.env]")
        return 2
    cred_path = Path(sys.argv[1]).expanduser()
    env_file = Path(sys.argv[2]) if len(sys.argv) == 3 else DEFAULT_ENV
    if not cred_path.is_file():
        raise RuntimeError("Downloaded OAuth Desktop client JSON not found")
    settings = json.loads(cred_path.read_text(encoding="utf-8"))
    installed = settings.get("installed")
    if not isinstance(installed, dict):
        raise RuntimeError("Create an OAuth client of type Desktop app (not Web app or service account)")
    client_id = installed.get("client_id", "")
    client_secret = installed.get("client_secret", "")
    if not client_id or not client_secret:
        raise RuntimeError("OAuth client ID/secret missing in downloaded JSON")
    if not env_file.is_file():
        raise RuntimeError(f"Create {env_file} by copying backend/.env.example first")
    sender = input("Gmail address to authorize as NOTHING sender: ").strip()
    if not re.fullmatch(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}", sender):
        raise RuntimeError("Invalid sender email")
    state = secrets.token_urlsafe(32)
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode("ascii")).digest()).decode("ascii").rstrip("=")
    result = {}

    class Callback(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Do not print OAuth codes or query strings.
        def do_GET(self):
            url = urllib.parse.urlsplit(self.path)
            if url.path != "/oauth/callback":
                self.send_error(404)
                return
            params = urllib.parse.parse_qs(url.query)
            if params.get("state", [""])[0] != state:
                self.send_error(403)
                return
            result["error"] = params.get("error", [""])[0]
            result["code"] = params.get("code", [""])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"<h2>Authorization received. Return to your Terminal.</h2>")

    server = http.server.HTTPServer(("127.0.0.1", 0), Callback)
    server.timeout = 180
    redirect = f"http://127.0.0.1:{server.server_port}/oauth/callback"
    url = AUTH_URL + "?" + urllib.parse.urlencode({
        "client_id": client_id, "redirect_uri": redirect, "response_type": "code",
        "scope": SCOPES, "access_type": "offline", "prompt": "consent",
        "state": state, "code_challenge": challenge, "code_challenge_method": "S256",
        "login_hint": sender})
    print("Opening your browser for Google consent (send email only).")
    print("If the browser doesn't open, use the URL printed below:")
    print(url)
    webbrowser.open(url)
    try:
        server.handle_request()
    finally:
        server.server_close()
    if result.get("error") or not result.get("code"):
        raise RuntimeError("Google OAuth consent was not completed (or timed out)")
    response = form_request(TOKEN_URL, {
        "code": result["code"], "client_id": client_id, "client_secret": client_secret,
        "redirect_uri": redirect, "grant_type": "authorization_code",
        "code_verifier": verifier})
    refresh = response.get("refresh_token")
    scopes = response.get("scope", "").split()
    if not refresh or SCOPES not in scopes:
        raise RuntimeError("OAuth did not return an offline gmail.send refresh token; try consenting again")
    update_private_env(env_file, {
        "MAIL_PROVIDER": "gmail-api",
        "GMAIL_OAUTH_CLIENT_ID": client_id,
        "GMAIL_OAUTH_CLIENT_SECRET": client_secret,
        "GMAIL_OAUTH_REFRESH_TOKEN": refresh,
        "EMAIL_FROM": sender,
        "MAIL_NOTIFICATIONS_ENABLED": "true",
        "MAIL_HEALTH_ENABLED": "false"})
    print(f"Success: Gmail API authorized; private settings saved to {env_file}.")
    print("No secrets were printed. Restart Docker app containers to apply the settings.")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, json.JSONDecodeError) as exc:
        print("Setup failed:", exc, file=sys.stderr)
        sys.exit(1)
