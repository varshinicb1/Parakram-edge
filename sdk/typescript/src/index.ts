/** AgentOS Bridge SDK — discover and control phone hardware from AI agents. */

export interface DeviceInfo {
  id: string;
  name: string;
  ip: string;
  port: number;
  apiKey?: string;
  viaRelay?: boolean;
}

export interface Capability {
  id: string;
  name: string;
  description: string;
  available: boolean;
}

export class Parakram {
  private device: DeviceInfo | null = null;
  private relayUrl: string;
  private relayToken: string;

  constructor(opts?: { relayUrl?: string; relayToken?: string }) {
    this.relayUrl = opts?.relayUrl ?? "";
    this.relayToken = opts?.relayToken ?? "";
  }

  // ── Connection ──

  connect(device: DeviceInfo): this {
    this.device = device;
    return this;
  }

  connectRelay(deviceId: string, apiKey: string): this {
    if (!this.relayUrl) throw new Error("relayUrl not configured");
    this.device = {
      id: deviceId, name: deviceId, ip: "", port: 0,
      apiKey, viaRelay: true,
    };
    return this;
  }

  // ── HTTP helpers ──

  private get base(): string {
    const d = this.device;
    if (!d) throw new Error("Not connected. Call connect() first.");
    if (d.viaRelay) return `${this.relayUrl}/relay/${d.id}`;
    return `http://${d.ip}:${d.port}`;
  }

  private headers(): Record<string, string> {
    return this.device?.apiKey ? { "X-Agent-Key": this.device.apiKey } : {};
  }

  private async get<T>(path: string): Promise<T> {
    const r = await fetch(`${this.base}${path}`, { headers: this.headers() });
    if (!r.ok) throw new Error(`GET ${path}: ${r.status}`);
    return r.json();
  }

  private async post<T>(path: string, data: unknown): Promise<T> {
    const r = await fetch(`${this.base}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...this.headers() },
      body: JSON.stringify(data),
    });
    if (!r.ok) throw new Error(`POST ${path}: ${r.status}`);
    return r.json();
  }

  // ── API ──

  async capabilities(): Promise<Capability[]> {
    return this.get("/api/capabilities");
  }

  async sensors(): Promise<Record<string, unknown>> {
    return this.get("/api/sensors");
  }

  async status(): Promise<Record<string, unknown>> {
    return this.get("/api/status");
  }

  async cameraAuth(): Promise<string> {
    const res = await this.post<{ url: string }>("/camera-auth", {});
    return res.url;
  }

  // ── UTAP ──

  async utapCreate(table: string, data: Record<string, unknown>): Promise<unknown> {
    return this.post("/api/db/utap/create", { table, data });
  }

  async utapRead(table: string, where = "", limit = 100): Promise<unknown[]> {
    return this.post("/api/db/utap/read", { table, where, limit });
  }

  async utapUpdate(table: string, data: Record<string, unknown>, where: string): Promise<unknown> {
    return this.post("/api/db/utap/update", { table, data, where });
  }

  async utapDelete(table: string, where: string): Promise<unknown> {
    return this.post("/api/db/utap/delete", { table, where });
  }

  async utapTables(): Promise<unknown[]> {
    return this.get("/api/db/utap/tables");
  }
}
