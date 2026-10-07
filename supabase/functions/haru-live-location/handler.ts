const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store, max-age=0",
  "x-content-type-options": "nosniff",
};

function response(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

function isUuid(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function isToken(value: unknown): value is string {
  return typeof value === "string" &&
    /^[A-Za-z0-9_-]{32,96}$/.test(value);
}

function isPayload(value: unknown): value is string {
  return typeof value === "string" && value.length >= 16 && value.length <= 4096;
}

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function createHandler(supabase: any, clock: () => number = Date.now) {
return async (req: Request): Promise<Response> => {
  if (req.method !== "POST") {
    return response(405, { error: "POST required" });
  }

  const contentLength = Number(req.headers.get("content-length") || "0");
  if (contentLength > 8192) {
    return response(413, { error: "Request too large" });
  }

  let body: Record<string, unknown>;
  try {
    const reader = req.body?.getReader();
    if (!reader) return response(400, { error: "Missing body" });
    const chunks: Uint8Array[] = [];
    let bytes = 0;
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        bytes += value.byteLength;
        if (bytes > 8192) {
          await reader.cancel();
          return response(413, { error: "Request too large" });
        }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    const raw = new Uint8Array(bytes);
    let offset = 0;
    for (const chunk of chunks) { raw.set(chunk, offset); offset += chunk.byteLength; }
    const parsed = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(raw));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return response(400, { error: "JSON object required" });
    }
    body = parsed;
  } catch {
    return response(400, { error: "Invalid JSON" });
  }

  const action = body.action;
  const now = new Date(clock());
  if (!["create", "read", "update", "stop"].includes(String(action))) {
    return response(400, { error: "Unknown action" });
  }

  if (action === "create") {
    const sessionId = body.session_id;
    const readToken = body.read_token;
    const writeToken = body.write_token;
    const payload = body.payload;
    const ttl = Number(body.ttl_minutes ?? 60);

    if (!isUuid(sessionId) || !isToken(readToken) || !isToken(writeToken) || !isPayload(payload)) {
      return response(400, { error: "Invalid create request" });
    }

    if (!Number.isFinite(ttl)) return response(400, { error: "Invalid duration" });
    const ttlMinutes = Math.min(120, Math.max(15, Math.floor(ttl)));
    const expiresAt = new Date(now.getTime() + ttlMinutes * 60_000);

    const { error } = await supabase.from("haru_live_sessions").insert({
      id: sessionId,
      read_hash: await sha256Hex(readToken),
      write_hash: await sha256Hex(writeToken),
      payload,
      seq: 1,
      expires_at: expiresAt.toISOString(),
      updated_at: now.toISOString(),
    });

    if (error) {
      return response(error.code === "23505" ? 409 : 500, { error: "Could not create share" });
    }

    return response(201, {
      session_id: sessionId,
      seq: 1,
      expires_at: expiresAt.toISOString(),
    });
  }

  const sessionId = body.session_id;
  if (!isUuid(sessionId)) {
    return response(400, { error: "Invalid session" });
  }

  const { data: row, error: loadError } = await supabase
    .from("haru_live_sessions")
    .select("id,read_hash,write_hash,payload,seq,updated_at,expires_at")
    .eq("id", sessionId)
    .maybeSingle();

  if (loadError) {
    return response(500, { error: "Session lookup failed" });
  }
  if (!row) {
    return response(404, { error: "Share not found" });
  }

  const credential = action === "read" ? body.read_token : body.write_token;
  const expectedHash = action === "read" ? row.read_hash : row.write_hash;
  if (!isToken(credential) || await sha256Hex(credential) !== expectedHash) {
    return response(403, { error: "Invalid share token" });
  }

  if (new Date(row.expires_at).getTime() <= now.getTime()) {
    await supabase.from("haru_live_sessions").delete().eq("id", sessionId);
    return response(410, { error: "Share expired" });
  }

  if (action === "read") {
    const readToken = body.read_token;
    if (!isToken(readToken) || await sha256Hex(readToken) !== row.read_hash) {
      return response(403, { error: "Invalid share token" });
    }

    return response(200, {
      payload: row.payload,
      seq: row.seq,
      updated_at: row.updated_at,
      expires_at: row.expires_at,
    });
  }

  if (action === "update") {
    const lastUpdatedMs = new Date(row.updated_at).getTime();
    if (Number.isFinite(lastUpdatedMs) && now.getTime() - lastUpdatedMs < 3000) {
      return response(429, { error: "Update interval too short" });
    }

    const writeToken = body.write_token;
    const payload = body.payload;
    const expectedSeq = Number(body.expected_seq ?? row.seq);

    if (!isToken(writeToken) || await sha256Hex(writeToken) !== row.write_hash) {
      return response(403, { error: "Invalid write token" });
    }
    if (!isPayload(payload)) {
      return response(400, { error: "Invalid payload" });
    }
    if (!Number.isSafeInteger(expectedSeq) || expectedSeq < 1) {
      return response(400, { error: "Invalid sequence" });
    }

    if (expectedSeq !== Number(row.seq)) {
      return response(409, { error: "Share sequence changed; restart sharing" });
    }
    const nextSeq = expectedSeq + 1;
    const { data: updated, error } = await supabase
      .from("haru_live_sessions")
      .update({
        payload,
        seq: nextSeq,
        updated_at: now.toISOString(),
      })
      .eq("id", sessionId)
      .eq("write_hash", row.write_hash)
      .eq("seq", expectedSeq)
      .gt("expires_at", now.toISOString())
      .select("seq")
      .maybeSingle();

    if (error) {
      return response(500, { error: "Update failed" });
    }

    if (!updated) return response(409, { error: "Share changed or stopped" });
    return response(200, {
      seq: nextSeq,
      updated_at: now.toISOString(),
      expires_at: row.expires_at,
    });
  }

  if (action === "stop") {
    const writeToken = body.write_token;
    if (!isToken(writeToken) || await sha256Hex(writeToken) !== row.write_hash) {
      return response(403, { error: "Invalid write token" });
    }

    const { error } = await supabase.from("haru_live_sessions").delete()
      .eq("id", sessionId)
      .eq("write_hash", row.write_hash);
    if (error) return response(500, { error: "Could not revoke share" });

    return response(200, { stopped: true });
  }

  return response(400, { error: "Unknown action" });
};
}

