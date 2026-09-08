// worker.js

// This is the binding name you'll configure in your Worker's settings
// to link to the KV namespace you created (e.g., kv_cfupdater)
// Ensure this matches the binding name in your wrangler.toml or Cloudflare dashboard settings.
// For this example, we'll assume the binding is named: kv_cfupdater

// Secret for simple API key authentication (change this to a strong, unique secret)
// Set API_SECRET as an encrypted Worker secret in Cloudflare settings.
const AUTH_HEADER = "X-API-Key";

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);
        const pathSegments = url.pathname.split('/').filter(Boolean); // e.g., ["names", "accountId", "groupId", "ipAddress"]

        // Basic Authentication Check
        const apiKey = request.headers.get(AUTH_HEADER);
        if (!env.API_SECRET || apiKey !== env.API_SECRET) {
            return new Response("Unauthorized", { status: 401 });
        }

        if (pathSegments.length < 1 || pathSegments[0] !== 'names') {
            return new Response("Invalid path. Expected /names/...", { status: 400 });
        }

        // --- Operations for a specific IP name: /names/{accountId}/{groupId}/{ipAddress} ---
        if (pathSegments.length === 4) {
            const [, accountId, groupId, ipAddressEncoded] = pathSegments;
            let ipAddress;
            try { ipAddress = decodeURIComponent(ipAddressEncoded).replace(/_/g, '.').replace(/-/g, ':'); }
            catch { return new Response('Invalid IP path', { status: 400 }); }
            const kvKey = `name:${accountId}:${groupId}:${ipAddress}`;

            switch (request.method) {
                case "GET":
                    try {
                        const value = await env.kv_cfupdater.get(kvKey);
                        if (value === null) {
                            return new Response(JSON.stringify({ name: null, message: "Name not found" }), {
                                status: 404,
                                headers: { 'Content-Type': 'application/json' },
                            });
                        }
                        return new Response(JSON.stringify({ name: value }), {
                            headers: { 'Content-Type': 'application/json' },
                        });
                    } catch (e) {
                        return new Response(`KV GET error: ${e.message}`, { status: 500 });
                    }

                case "PUT":
                    try {
                        const body = await request.json();
                        if (!body || typeof body.name !== 'string' || body.name.trim() === '' || body.name.trim().length > 100) {
                            return new Response("Invalid request body. JSON with non-empty 'name' string required.", { status: 400 });
                        }
                        await env.kv_cfupdater.put(kvKey, body.name.trim(), { metadata: { name: body.name.trim() } });
                        return new Response(JSON.stringify({ message: "Name saved successfully", key: kvKey, name: body.name.trim() }), {
                            status: 200,
                            headers: { 'Content-Type': 'application/json' },
                        });
                    } catch (e) {
                        if (e instanceof SyntaxError) {
                            return new Response("Invalid JSON body.", { status: 400 });
                        }
                        return new Response(`KV PUT error or invalid body: ${e.message}`, { status: 500 });
                    }

                case "DELETE":
                    try {
                        await env.kv_cfupdater.delete(kvKey);
                        return new Response(JSON.stringify({ message: "Name deleted successfully", key: kvKey }), {
                            status: 200,
                            headers: { 'Content-Type': 'application/json' },
                        });
                    } catch (e) {
                        return new Response(`KV DELETE error: ${e.message}`, { status: 500 });
                    }

                default:
                    return new Response("Method not allowed for this endpoint.", { status: 405 });
            }
        }

        // --- Operation to list all names for a group: /names/{accountId}/{groupId} ---
        if (pathSegments.length === 3 && request.method === "GET") {
            const [, accountId, groupId] = pathSegments;
            const prefix = `name:${accountId}:${groupId}:`;
            try {
                const namesMap = {};
                let cursor;
                do {
                    const listResult = await env.kv_cfupdater.list({ prefix, cursor });
                    for (const key of listResult.keys) {
                        const ip = key.name.substring(prefix.length);
                        // Metadata avoids one additional KV read per name; support old entries too.
                        const name = typeof key.metadata?.name === 'string'
                            ? key.metadata.name : await env.kv_cfupdater.get(key.name);
                        if (name !== null) namesMap[ip] = name;
                    }
                    if (listResult.list_complete) break;
                    cursor = listResult.cursor;
                } while (cursor);
                return new Response(JSON.stringify(namesMap), {
                    headers: { 'Content-Type': 'application/json' },
                });
            } catch (e) {
                return new Response(`KV LIST error: ${e.message}`, { status: 500 });
            }
        }

        return new Response("Endpoint not found. Use /names/{accountId}/{groupId}/{ipAddress} or GET /names/{accountId}/{groupId}", { status: 404 });
    },
};

