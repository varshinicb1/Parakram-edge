import type { D1Database } from "@cloudflare/workers-types";

export interface Env {
  DB: D1Database;
  REGISTRY_KEY: string;
}

interface PluginBody {
  name?: string;
  display_name?: string;
  description?: string;
  author?: string;
  category?: string;
  icon_url?: string;
  tier?: "free" | "pro" | "enterprise";
  homepage?: string;
}

interface VersionBody {
  version?: string;
  code_url?: string;
  min_sdk_version?: string;
  changelog?: string;
  checksum?: string;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
  });
}

function auth(env: Env, req: Request): boolean {
  const key = req.headers.get("X-Registry-Key");
  return key === env.REGISTRY_KEY;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;

    // CORS preflight
    if (method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE",
          "Access-Control-Allow-Headers": "Content-Type,X-Registry-Key",
        },
      });
    }

    try {
      return await handleRequest(method, url.pathname, request, env);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      return json({ error: msg }, 400);
    }
  },
};

async function handleRequest(method: string, path: string, request: Request, env: Env): Promise<Response> {
  const { DB } = env;

  // ── Plugin listing ──────────────────────────────────────────
  if (method === "GET" && path === "/api/plugins") {
    const category = new URL(request.url).searchParams.get("category");
    let q = "SELECT id, name, display_name, description, author, category, icon_url, tier, homepage, created_at, updated_at FROM plugins";
    const params: unknown[] = [];
    if (category) {
      q += " WHERE category = ?";
      params.push(category);
    }
    q += " ORDER BY downloads DESC";
    const { results } = await DB.prepare(q).bind(...params).all();
    return json(results);
  }

  // ── Single plugin ───────────────────────────────────────────
  const pluginMatch = path.match(/^\/api\/plugins\/([^/]+)$/);
  if (pluginMatch) {
    const nameOrId = pluginMatch[1];

    if (method === "GET") {
      const row = await DB.prepare(
        "SELECT id, name, display_name, description, author, category, icon_url, tier, homepage, created_at, updated_at FROM plugins WHERE id = ? OR name = ?"
      ).bind(nameOrId, nameOrId).first();
      return json(row || { error: "not found" }, row ? 200 : 404);
    }

    if (!auth(env, request)) return json({ error: "unauthorized" }, 403);

    if (method === "PUT") {
      const body: PluginBody = await request.json();
      const allowed = ["display_name","description","author","category","icon_url","tier","homepage"];
      const updates: string[] = [];
      const params: unknown[] = [];
      for (const k of allowed) {
        const v = (body as Record<string, unknown>)[k];
        if (v !== undefined) { updates.push(`${k} = ?`); params.push(v); }
      }
      if (!updates.length) return json({ error: "no fields" });
      params.push(nameOrId);
      await DB.prepare(`UPDATE plugins SET ${updates.join(", ")}, updated_at = datetime('now') WHERE id = ? OR name = ?`).bind(...params, nameOrId).run();
      return json({ ok: true });
    }

    if (method === "DELETE") {
      await DB.prepare("DELETE FROM plugins WHERE id = ? OR name = ?").bind(nameOrId, nameOrId).run();
      return json({ ok: true });
    }
  }

  // ── Create plugin ───────────────────────────────────────────
  if (method === "POST" && path === "/api/plugins") {
    if (!auth(env, request)) return json({ error: "unauthorized" }, 403);
    const body: PluginBody = await request.json();
    if (!body.name || !body.display_name) return json({ error: "name and display_name required" });
    await DB.prepare(
      "INSERT INTO plugins (name, display_name, description, author, category, icon_url, tier, homepage) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    ).bind(
      body.name, body.display_name, body.description ?? "",
      body.author ?? "", body.category ?? "utility",
      body.icon_url ?? "", body.tier ?? "free", body.homepage ?? ""
    ).run();
    return json({ ok: true }, 201);
  }

  // ── Versions ────────────────────────────────────────────────
  const versionMatch = path.match(/^\/api\/plugins\/([^/]+)\/versions(?:\/([^/]+))?$/);
  if (versionMatch) {
    const [, pluginName, versionTag] = versionMatch;

    if (method === "GET" && !versionTag) {
      const { results } = await DB.prepare(
        `SELECT v.id, v.version, v.code_url, v.min_sdk_version, v.changelog, v.checksum, v.downloads, v.created_at
         FROM versions v JOIN plugins p ON v.plugin_id = p.id
         WHERE p.name = ? ORDER BY v.created_at DESC`
      ).bind(pluginName).all();
      return json(results);
    }

    if (!auth(env, request)) return json({ error: "unauthorized" }, 403);

    if (method === "POST" && !versionTag) {
      const body: VersionBody = await request.json();
      if (!body.version || !body.code_url) return json({ error: "version and code_url required" });
      const plugin = await DB.prepare("SELECT id FROM plugins WHERE name = ?").bind(pluginName).first<{ id: number }>();
      if (!plugin) return json({ error: "plugin not found" }, 404);
      await DB.prepare(
        "INSERT INTO versions (plugin_id, version, code_url, min_sdk_version, changelog, checksum) VALUES (?, ?, ?, ?, ?, ?)"
      ).bind(plugin.id, body.version, body.code_url, body.min_sdk_version ?? "1.0.0", body.changelog ?? "", body.checksum ?? "").run();
      return json({ ok: true }, 201);
    }
  }

  // ── Installations ───────────────────────────────────────────
  if (method === "POST" && path === "/api/install") {
    const { plugin_name, device_id, version } = await request.json() as Record<string, string>;
    if (!plugin_name || !device_id) return json({ error: "plugin_name and device_id required" });
    const plugin = await DB.prepare("SELECT id FROM plugins WHERE name = ?").bind(plugin_name).first<{ id: number }>();
    if (!plugin) return json({ error: "plugin not found" }, 404);
    await DB.prepare(
      "INSERT OR REPLACE INTO installations (plugin_id, device_id, version, enabled) VALUES (?, ?, ?, 1)"
    ).bind(plugin.id, device_id, version ?? "latest").run();
    return json({ ok: true });
  }

  if (method === "GET" && path.startsWith("/api/installed/")) {
    const deviceId = path.slice("/api/installed/".length);
    const { results } = await DB.prepare(
      `SELECT p.name, p.display_name, i.version, i.enabled, i.installed_at
       FROM installations i JOIN plugins p ON i.plugin_id = p.id
       WHERE i.device_id = ? ORDER BY i.installed_at DESC`
    ).bind(deviceId).all();
    return json(results);
  }

  return json({ error: "not found" }, 404);
}
