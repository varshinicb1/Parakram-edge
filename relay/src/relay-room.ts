import { DurableObject } from "cloudflare:workers";

type Role = "phone" | "agent";

interface PeerInfo {
  ws: WebSocket;
  role: Role;
}

export class RelayRoom extends DurableObject {
  private phone: WebSocket | null = null;
  private agent: WebSocket | null = null;

  async fetch(request: Request, role?: Role): Promise<Response> {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    this.ctx.acceptWebSocket(server);

    if (role === "phone") {
      this.phone = server;
    } else if (role === "agent") {
      this.agent = server;
    }

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer) {
    const target = this.getOther(ws);
    if (target) {
      target.send(message);
    }
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean) {
    if (this.phone === ws) {
      this.phone = null;
    }
    if (this.agent === ws) {
      this.agent = null;
    }
    ws.close(code, reason);
  }

  async webSocketError(ws: WebSocket, error: unknown) {
    if (this.phone === ws) this.phone = null;
    if (this.agent === ws) this.agent = null;
  }

  private getOther(ws: WebSocket): WebSocket | null {
    if (this.phone === ws) return this.agent;
    if (this.agent === ws) return this.phone;
    return null;
  }
}
