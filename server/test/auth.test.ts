import assert from "node:assert/strict";
import { test } from "node:test";
import worker from "../src/index.ts";

const env = { ANTHROPIC_API_KEY: "unused", FIREBASE_PROJECT_ID: "demo-project" };
const post = (path: string, headers: Record<string, string> = {}) =>
  worker.fetch(new Request(`https://api.test${path}`, { method: "POST", headers, body: JSON.stringify({ url: "https://www.instagram.com/reel/C9xYz_12ab/" }) }), env);

test("health check is open", async () => assert.equal((await worker.fetch(new Request("https://api.test/"), env)).status, 200));

test("AI endpoints reject missing and forged tokens before any Claude call", async () => {
  assert.equal((await post("/reel")).status, 401);
  const forged = ["eyJhbGciOiJSUzI1NiJ9", btoa(JSON.stringify({ sub: "x", aud: "demo-project" })), "sig"].join(".");
  assert.equal((await post("/summarize", { authorization: `Bearer ${forged}` })).status, 401);
});
