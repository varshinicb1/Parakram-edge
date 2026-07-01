import json
import socket
from typing import Optional
from urllib.parse import urljoin

import httpx
from zeroconf import ServiceBrowser, Zeroconf, ServiceStateChange

from .models import DeviceInfo


class Parakram:
    """AgentOS Bridge — discover and control phone hardware from AI agents."""

    def __init__(self, relay_url: str = "", relay_token: str = ""):
        self._http = httpx.Client(timeout=10)
        self._device: Optional[DeviceInfo] = None
        self._relay_url = relay_url.rstrip("/")
        self._relay_token = relay_token

    # ── Discovery ──────────────────────────────────────────────

    def discover(self, timeout: float = 2.0) -> list[DeviceInfo]:
        """Discover Parakram devices on the local network via mDNS."""
        found: list[DeviceInfo] = []

        class Listener:
            def add_service(self, zconf, typ, name):
                info = zconf.get_service_info(typ, name)
                if info and info.parsed_addresses():
                    ip = info.parsed_addresses()[0]
                    port = info.port or 8080
                    found.append(DeviceInfo(
                        id=name.split(".")[0],
                        name=info.server,
                        ip=ip,
                        port=port,
                    ))

            def remove_service(self, *a): pass

        zc = Zeroconf()
        try:
            ServiceBrowser(zc, "_parakram._tcp.local.", Listener())
            import time
            time.sleep(timeout)
        finally:
            zc.close()

        return found

    # ── Connection ─────────────────────────────────────────────

    def connect(self, device: DeviceInfo) -> "Parakram":
        """Connect to a discovered device."""
        self._device = device
        return self

    def connect_relay(self, device_id: str, api_key: str) -> "Parakram":
        """Connect to a device over the cloud relay (internet tunnel)."""
        if not self._relay_url:
            raise RuntimeError("relay_url not configured")
        self._device = DeviceInfo(
            id=device_id, name=device_id, ip="", port=0,
            api_key=api_key, via_relay=True,
        )
        return self

    # ── API helpers ────────────────────────────────────────────

    @property
    def _base(self) -> str:
        d = self._device
        if not d:
            raise RuntimeError("Not connected. Call connect() first.")
        if d.via_relay:
            return f"{self._relay_url}/relay/{d.id}"
        return f"http://{d.ip}:{d.port}"

    def _headers(self) -> dict:
        return {"X-Agent-Key": self._device.api_key} if self._device and self._device.api_key else {}

    def _get(self, path: str) -> dict:
        r = self._http.get(urljoin(self._base, path), headers=self._headers())
        r.raise_for_status()
        return r.json()

    def _post(self, path: str, data: dict) -> dict:
        r = self._http.post(urljoin(self._base, path), json=data, headers=self._headers())
        r.raise_for_status()
        return r.json()

    # ── Hardware capabilities ──────────────────────────────────

    @property
    def capabilities(self) -> list[dict]:
        """List all available phone hardware capabilities."""
        return self._get("/api/capabilities")

    @property
    def sensors(self) -> dict:
        """Read current sensor values (battery, motion, location, etc.)."""
        return self._get("/api/sensors")

    @property
    def status(self) -> dict:
        """Get server status and device info."""
        return self._get("/api/status")

    # ── Camera ─────────────────────────────────────────────────

    def camera_auth(self) -> str:
        """Get a one-time signed camera stream URL."""
        return self._post("/camera-auth", {})["url"]

    # ── File system ────────────────────────────────────────────

    def list_files(self, path: str = "/") -> list[dict]:
        return self._get(f"/api/files?path={path}")

    def read_file(self, path: str) -> bytes:
        r = self._http.get(urljoin(self._base, f"/api/files/read?path={path}"), headers=self._headers())
        r.raise_for_status()
        return r.content

    # ── UTAP (Universal agentic Table Access Protocol) ─────────

    def utap_create(self, table: str, data: dict) -> dict:
        return self._post("/api/db/utap/create", {"table": table, "data": data})

    def utap_read(self, table: str, where: str = "", limit: int = 100) -> list[dict]:
        return self._post("/api/db/utap/read", {"table": table, "where": where, "limit": limit})

    def utap_update(self, table: str, data: dict, where: str) -> dict:
        return self._post("/api/db/utap/update", {"table": table, "data": data, "where": where})

    def utap_delete(self, table: str, where: str) -> dict:
        return self._post("/api/db/utap/delete", {"table": table, "where": where})

    def utap_tables(self) -> list[dict]:
        return self._get("/api/db/utap/tables")
