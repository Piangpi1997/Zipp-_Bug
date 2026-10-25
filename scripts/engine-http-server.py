#!/usr/bin/env python3
"""
ZIP_BUG Authenticated Companion HTTP Engine Server (v1.0.0)

Provides the minimal, security-hardened HTTP engine required by EngineConnectionClient:
- GET  /health
- POST /api/jobs
- GET  /api/jobs/<jobId>/logs
- POST /api/jobs/<jobId>/cancel
- GET  /api/jobs/<jobId>/artifact?path=<relativePath>

Security guarantees:
1. Mandatory Bearer token authentication on all endpoints (hmac.compare_digest).
   Never exposes an unauthenticated command execution endpoint.
2. Strict tool allow-list (shell=False, no arbitrary shell command evaluation).
3. Canonical path validation on working directories and artifact retrieval.
"""

import argparse
import hmac
import http.server
import json
import os
import secrets
import shutil
import socketserver
import subprocess
import sys
import threading
import time
import urllib.parse
import uuid

ENGINE_VERSION = "1.0.0"

ALLOWED_TOOLS = {
    "python": "python",
    "java": "java",
    "node": "node",
    "npm": "npm",
    "ffmpeg": "ffmpeg",
    "yt-dlp": "yt-dlp",
    "gradle": "gradle",
    "git": "git",
    "aapt2": "aapt2",
    "edge-tts": "edge-tts",
}


class JobRecord:
    def __init__(self, job_id: str, tool: str, args: list, work_dir: str):
        self.job_id = job_id
        self.tool = tool
        self.args = args
        self.work_dir = work_dir
        self.status = "RUNNING"
        self.stdout = ""
        self.stderr = ""
        self.exit_code = None
        self.message = "Job running"
        self.process = None
        self.lock = threading.Lock()

    def to_dict(self) -> dict:
        with self.lock:
            artifacts = []
            if os.path.isdir(self.work_dir):
                for root, dirs, files in os.walk(self.work_dir):
                    dirs[:] = [d for d in dirs if d not in ("node_modules", ".git", ".gradle")]
                    for f in files:
                        full = os.path.join(root, f)
                        rel = os.path.relpath(full, self.work_dir).replace("\\", "/")
                        artifacts.append(rel)
            return {
                "accepted": True,
                "jobId": self.job_id,
                "tool": self.tool,
                "status": self.status,
                "stdout": self.stdout,
                "stderr": self.stderr,
                "exitCode": self.exit_code,
                "message": self.message,
                "artifacts": sorted(artifacts)[:50],
            }


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def detect_capabilities() -> list:
    caps = []
    for name, binary in ALLOWED_TOOLS.items():
        if shutil.which(binary):
            caps.append(name)
    return caps


def run_job_process(record: JobRecord, binary_path: str):
    try:
        os.makedirs(record.work_dir, exist_ok=True)
        proc = subprocess.Popen(
            [binary_path, *record.args],
            cwd=record.work_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            shell=False,
        )
        with record.lock:
            record.process = proc

        out, err = proc.communicate(timeout=120)
        with record.lock:
            if record.status == "CANCELLED":
                record.stdout = out or ""
                record.stderr = err or ""
                record.exit_code = proc.returncode
                record.message = "Job cancelled by user"
            else:
                record.stdout = out or ""
                record.stderr = err or ""
                record.exit_code = proc.returncode
                if proc.returncode == 0:
                    record.status = "COMPLETED"
                    record.message = f"{record.tool} completed (exit 0)"
                else:
                    record.status = "FAILED"
                    record.message = f"{record.tool} exited with code {proc.returncode}"
    except Exception as exc:
        with record.lock:
            if record.status != "CANCELLED":
                record.status = "FAILED"
                record.stderr = str(exc)
                record.exit_code = -1
                record.message = f"Execution error: {exc}"


def make_handler(expected_token: str, base_workspace: str, jobs_store: dict, jobs_lock: threading.Lock):
    class EngineRequestHandler(http.server.BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            sys.stderr.write(f"[ZipBugEngine] {self.address_string()} - {fmt % args}\n")

        def _send_json(self, status_code: int, payload: dict):
            raw = json.dumps(payload).encode("utf-8")
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def _check_auth(self) -> bool:
            auth_header = self.headers.get("Authorization", "")
            if not auth_header.startswith("Bearer "):
                self._send_json(
                    401,
                    {"ok": False, "status": "UNAUTHORIZED", "error": "Missing Bearer token"},
                )
                return False
            provided = auth_header[len("Bearer "):].strip()
            if not provided or not hmac.compare_digest(provided, expected_token):
                self._send_json(
                    401,
                    {"ok": False, "status": "UNAUTHORIZED", "error": "Invalid Bearer token"},
                )
                return False
            return True

        def do_GET(self):
            if not self._check_auth():
                return

            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path.rstrip("/") or "/"

            if path == "/health":
                caps = detect_capabilities()
                self._send_json(
                    200,
                    {
                        "ok": True,
                        "status": "ok",
                        "version": ENGINE_VERSION,
                        "capabilities": caps,
                    },
                )
                return

            if path.startswith("/api/jobs/"):
                parts = path.strip("/").split("/")
                # /api/jobs/<jobId> or /api/jobs/<jobId>/logs or /api/jobs/<jobId>/artifact
                if len(parts) >= 3:
                    job_id = parts[2]
                    action = parts[3] if len(parts) >= 4 else "logs"
                    with jobs_lock:
                        record = jobs_store.get(job_id)
                    if record is None:
                        self._send_json(404, {"accepted": False, "status": "NOT_FOUND", "message": "Job not found"})
                        return

                    if action in ("logs", "status"):
                        self._send_json(200, record.to_dict())
                        return

                    if action == "artifact":
                        qs = urllib.parse.parse_qs(parsed.query)
                        rel_path = (qs.get("path") or [""])[0].strip()
                        if not rel_path or ".." in rel_path:
                            self._send_json(400, {"error": "Invalid artifact path"})
                            return
                        canon_root = os.path.realpath(record.work_dir)
                        canon_target = os.path.realpath(os.path.join(canon_root, rel_path))
                        if not canon_target.startswith(canon_root + os.sep) or not os.path.isfile(canon_target):
                            self._send_json(404, {"error": f"Artifact not found: {rel_path}"})
                            return
                        with open(canon_target, "rb") as f:
                            data = f.read()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/octet-stream")
                        self.send_header("Content-Length", str(len(data)))
                        self.end_headers()
                        self.wfile.write(data)
                        return

            self._send_json(404, {"error": f"Unknown endpoint: {path}"})

        def do_POST(self):
            if not self._check_auth():
                return

            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path.rstrip("/")

            if path == "/api/jobs":
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > 2 * 1024 * 1024:
                    self._send_json(400, {"accepted": False, "message": "Invalid request payload size"})
                    return
                body = self.rfile.read(length).decode("utf-8", errors="replace")
                try:
                    data = json.loads(body)
                except Exception:
                    self._send_json(400, {"accepted": False, "message": "Malformed JSON"})
                    return

                tool = str(data.get("tool", "")).strip()
                raw_args = data.get("args", [])
                async_mode = bool(data.get("async", False))
                if tool not in ALLOWED_TOOLS:
                    self._send_json(400, {"accepted": False, "message": f"Tool not allowed: {tool}"})
                    return
                if not isinstance(raw_args, list) or any(not isinstance(a, str) for a in raw_args):
                    self._send_json(400, {"accepted": False, "message": "args must be a list of strings"})
                    return
                for a in raw_args:
                    if "\x00" in a or len(a) > 4096:
                        self._send_json(400, {"accepted": False, "message": "Invalid argument content"})
                        return

                binary_path = shutil.which(ALLOWED_TOOLS[tool])
                if not binary_path:
                    self._send_json(
                        400,
                        {"accepted": False, "status": "FAILED", "message": f"Binary not installed on host: {tool}"},
                    )
                    return

                job_id = str(uuid.uuid4())
                req_workdir = str(data.get("workDir", "")).strip()
                if req_workdir and os.path.isabs(req_workdir) and os.path.isdir(req_workdir):
                    work_dir = os.path.realpath(req_workdir)
                else:
                    work_dir = os.path.realpath(os.path.join(base_workspace, job_id))
                    os.makedirs(work_dir, exist_ok=True)

                record = JobRecord(job_id, tool, raw_args, work_dir)
                with jobs_lock:
                    jobs_store[job_id] = record

                if async_mode:
                    t = threading.Thread(target=run_job_process, args=(record, binary_path), daemon=True)
                    t.start()
                    time.sleep(0.05)
                    self._send_json(202, record.to_dict())
                else:
                    run_job_process(record, binary_path)
                    self._send_json(200, record.to_dict())
                return

            if path.startswith("/api/jobs/") and path.endswith("/cancel"):
                parts = path.strip("/").split("/")
                if len(parts) == 4:
                    job_id = parts[2]
                    with jobs_lock:
                        record = jobs_store.get(job_id)
                    if record is None:
                        self._send_json(404, {"accepted": False, "status": "NOT_FOUND", "message": "Job not found"})
                        return
                    with record.lock:
                        record.status = "CANCELLED"
                        record.message = "Job cancelled by user"
                        proc = record.process
                    if proc is not None and proc.poll() is None:
                        try:
                            proc.terminate()
                            proc.wait(timeout=2)
                        except Exception:
                            try:
                                proc.kill()
                            except Exception:
                                pass
                    self._send_json(200, record.to_dict())
                    return

            self._send_json(404, {"error": f"Unknown POST endpoint: {path}"})

    return EngineRequestHandler


def main():
    parser = argparse.ArgumentParser(description="ZIP_BUG Authenticated Companion HTTP Engine")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address (127.0.0.1 for loopback, or LAN IP)")
    parser.add_argument("--port", type=int, default=8090, help="Bind port (default: 8090)")
    parser.add_argument("--token", default=os.environ.get("ZIPBUG_ENGINE_TOKEN", ""), help="Mandatory Bearer token")
    parser.add_argument(
        "--workspace",
        default=os.path.join(os.path.expanduser("~"), ".zipbug-engine-jobs"),
        help="Base directory for isolated job workspaces",
    )
    args = parser.parse_args()

    token = args.token.strip()
    if not token:
        token = secrets.token_hex(16)
        print(f"[ZipBugEngine] Generated session Bearer token: {token}", flush=True)

    os.makedirs(args.workspace, exist_ok=True)
    jobs_store = {}
    jobs_lock = threading.Lock()

    handler_cls = make_handler(token, args.workspace, jobs_store, jobs_lock)
    server = ThreadedHTTPServer((args.host, args.port), handler_cls)
    caps = detect_capabilities()
    print(
        f"[ZipBugEngine] Listening on http://{args.host}:{args.port} "
        f"(version={ENGINE_VERSION}, capabilities={caps})",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
