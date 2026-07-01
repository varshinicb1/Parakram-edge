import Razorpay from "razorpay";

export interface Env {
  DB: D1Database;
  RAZORPAY_KEY_ID: string;
  RAZORPAY_KEY_SECRET: string;
  RAZORPAY_WEBHOOK_SECRET: string;
  RAZORPAY_PRO_PLAN_ID: string;
  RAZORPAY_ENTERPRISE_PLAN_ID: string;
  FRONTEND_URL: string;
}

interface SubscriptionRecord {
  id: number;
  customer_id: string;
  tier: "free" | "pro" | "enterprise";
  razorpay_subscription_id: string | null;
  razorpay_customer_id: string | null;
  status: "active" | "cancelled" | "past_due" | "paused";
  current_period_end: string | null;
  created_at: string;
  updated_at: string;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
  });
}

function getRazorpay(env: Env): Razorpay {
  return new Razorpay({
    key_id: env.RAZORPAY_KEY_ID,
    key_secret: env.RAZORPAY_KEY_SECRET,
  });
}

async function verifyWebhookSignature(env: Env, payload: string, signature: string | null): Promise<boolean> {
  if (!signature) return false;
  const crypto = require("crypto");
  const expected = crypto.createHmac("sha256", env.RAZORPAY_WEBHOOK_SECRET).update(payload).digest("hex");
  return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected));
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;

    if (method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type,X-Razorpay-Signature",
        },
      });
    }

    try {
      // ── Create subscription checkout ─────────────────────────────
      if (method === "POST" && url.pathname === "/subscription/create") {
        const { tier, customer_email, customer_name, device_id } = await request.json() as {
          tier: "pro" | "enterprise";
          customer_email: string;
          customer_name?: string;
          device_id: string;
        };

        if (!tier || !customer_email || !device_id) {
          return json({ error: "tier, customer_email, device_id required" }, 400);
        }

        const rzp = getRazorpay(env);
        const planId = tier === "pro" ? env.RAZORPAY_PRO_PLAN_ID : env.RAZORPAY_ENTERPRISE_PLAN_ID;
        const totalCount = tier === "pro" ? 12 : 12; // 12 months

        // Create Razorpay customer if not exists
        let customer;
        try {
          customer = await rzp.customers.create({
            email: customer_email,
            name: customer_name || customer_email.split("@")[0],
            notes: { device_id, tier },
          });
        } catch (e: unknown) {
          const err = e as { error?: { code?: string } };
          if (err.error?.code === "BAD_REQUEST_ERROR") {
            const customers = await rzp.customers.all({ email: customer_email });
            customer = customers.items[0];
          } else throw e;
        }

        // Create subscription
        const subscription = await rzp.subscriptions.create({
          plan_id: planId,
          customer_id: customer.id,
          total_count: totalCount,
          notes: { device_id, tier },
        });

        // Store in D1
        await env.DB.prepare(
          `INSERT OR REPLACE INTO subscriptions 
           (customer_id, tier, razorpay_subscription_id, razorpay_customer_id, status, current_period_end, created_at, updated_at)
           VALUES (?, ?, ?, ?, 'active', ?, datetime('now'), datetime('now'))`
        ).bind(
          device_id,
          tier,
          subscription.id,
          customer.id,
          new Date(subscription.current_period_end * 1000).toISOString()
        ).run();

        const checkoutUrl = `${env.FRONTEND_URL}/billing/checkout?subscription_id=${subscription.id}&key=${env.RAZORPAY_KEY_ID}`;
        return json({ checkout_url: checkoutUrl, subscription_id: subscription.id });
      }

      // ── Razorpay webhook ─────────────────────────────────────────
      if (method === "POST" && url.pathname === "/webhook/razorpay") {
        const signature = request.headers.get("X-Razorpay-Signature");
        const payload = await request.text();

        if (!await verifyWebhookSignature(env, payload, signature)) {
          return json({ error: "Invalid signature" }, 400);
        }

        const event = JSON.parse(payload);
        const rzp = getRazorpay(env);

        switch (event.event) {
          case "subscription.charged": {
            const sub = event.payload.subscription.entity;
            const deviceId = sub.notes?.device_id;
            if (deviceId) {
              await env.DB.prepare(
                `UPDATE subscriptions SET status = 'active', current_period_end = ?, updated_at = datetime('now')
                 WHERE razorpay_subscription_id = ?`
              ).bind(new Date(sub.current_period_end * 1000).toISOString(), sub.id).run();
            }
            break;
          }
          case "subscription.cancelled": {
            const sub = event.payload.subscription.entity;
            await env.DB.prepare(
              `UPDATE subscriptions SET status = 'cancelled', updated_at = datetime('now')
               WHERE razorpay_subscription_id = ?`
            ).bind(sub.id).run();
            break;
          }
          case "subscription.paused": {
            const sub = event.payload.subscription.entity;
            await env.DB.prepare(
              `UPDATE subscriptions SET status = 'paused', updated_at = datetime('now')
               WHERE razorpay_subscription_id = ?`
            ).bind(sub.id).run();
            break;
          }
          case "subscription.resumed": {
            const sub = event.payload.subscription.entity;
            await env.DB.prepare(
              `UPDATE subscriptions SET status = 'active', updated_at = datetime('now')
               WHERE razorpay_subscription_id = ?`
            ).bind(sub.id).run();
            break;
          }
          case "payment.failed": {
            const payment = event.payload.payment.entity;
            const subId = payment.notes?.subscription_id;
            if (subId) {
              await env.DB.prepare(
                `UPDATE subscriptions SET status = 'past_due', updated_at = datetime('now')
                 WHERE razorpay_subscription_id = ?`
              ).bind(subId).run();
            }
            break;
          }
        }

        return json({ received: true });
      }

      // ── Get subscription status ──────────────────────────────────
      if (method === "GET" && url.pathname.startsWith("/subscription/status/")) {
        const deviceId = url.pathname.slice("/subscription/status/".length);
        const sub = await env.DB.prepare(
          "SELECT * FROM subscriptions WHERE customer_id = ? ORDER BY created_at DESC LIMIT 1"
        ).bind(deviceId).first<SubscriptionRecord>();

        if (!sub) {
          return json({ tier: "free", status: "inactive" });
        }

        return json({
          tier: sub.tier,
          status: sub.status,
          current_period_end: sub.current_period_end,
          razorpay_subscription_id: sub.razorpay_subscription_id,
        });
      }

      // ── List tiers/pricing ───────────────────────────────────────
      if (method === "GET" && url.pathname === "/tiers") {
        return json({
          free: { price: 0, currency: "INR", features: ["LAN-only", "1 device", "Community plugins"] },
          pro: { price: 499, currency: "INR", interval: "month", features: ["Internet relay", "Unlimited devices", "Pro plugins", "Priority relay"] },
          enterprise: { price: 4999, currency: "INR", interval: "month", per_seat: true, features: ["Fleet management", "SSO (Google)", "Audit logs", "SLA", "Dedicated relay", "Custom plugins"] },
        });
      }

      // ── Cancel subscription ──────────────────────────────────────
      if (method === "POST" && url.pathname === "/subscription/cancel") {
        const { device_id, at_period_end = true } = await request.json() as { device_id: string; at_period_end?: boolean };
        const sub = await env.DB.prepare(
          "SELECT razorpay_subscription_id FROM subscriptions WHERE customer_id = ? AND status = 'active' ORDER BY created_at DESC LIMIT 1"
        ).bind(deviceId).first<{ razorpay_subscription_id: string }>();

        if (!sub) return json({ error: "No active subscription" }, 404);

        const rzp = getRazorpay(env);
        await rzp.subscriptions.cancel(sub.razorpay_subscription_id, { cancel_at_cycle_end: at_period_end });

        await env.DB.prepare(
          `UPDATE subscriptions SET status = 'cancelled', updated_at = datetime('now') WHERE razorpay_subscription_id = ?`
        ).bind(sub.razorpay_subscription_id).run();

        return json({ ok: true });
      }

      return json({ error: "Not found" }, 404);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      return json({ error: msg }, 500);
    }
  },
};