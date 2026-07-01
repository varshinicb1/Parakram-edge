import { DurableObject } from "cloudflare:workers";

export interface Env {
  RELAY_ROOM: DurableObjectNamespace<RelayRoom>;
  RELAY_TOKEN: string;
}

type Role = "phone" | "agent";

interface ConnectQuery {
  deviceId: string;
  role: Role;
  token: string;
}

function parseQuery(url: string): ConnectQuery | null {
  const u = new URL(url);
  const deviceId = u.searchParams.get("deviceId");
  const role = u.searchParams.get("role") as Role | null;
  const token = u.searchParams.get("token");
  if (!deviceId || !role || !token) return null;
  if (role !== "phone" && role !== "agent") return null;
  return { deviceId, role, token };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "GET") {
      return new Response("Method not allowed", { status: 405 });
    }

    const upgrade = request.headers.get("Upgrade");
    if (!upgrade || upgrade !== "websocket") {
      return new Response("Expected WebSocket upgrade", { status: 426 });
    }

    const query = parseQuery(request.url);
    if (!query) {
      return new Response("Missing or invalid query params: deviceId, role (phone|agent), token", { status: 400 });
    }

    if (query.token !== env.RELAY_TOKEN) {
      return new Response("Invalid relay token", { status: 403 });
    }

    const roomId = `relay:${query.deviceId}`;
    const stub = env.RELAY_ROOM.getByName(roomId);
    return stub.fetch(request, query.role);
  },
};
