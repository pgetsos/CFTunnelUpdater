import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../CFWorker.js', import.meta.url), 'utf8');
const { default: worker } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
function fixture() {
    const values = new Map();
    const env = { API_SECRET: 'test-secret', kv_cfupdater: {
        async get(k) { return values.get(k)?.value ?? null; },
        async put(k, value, options) { values.set(k, { value, metadata: options?.metadata }); },
        async delete(k) { values.delete(k); },
        async list({ prefix, cursor }) {
            const keys = [...values].filter(([k]) => k.startsWith(prefix)).map(([name, v]) => ({ name, metadata: v.metadata }));
            const start = Number(cursor || 0);
            return { keys: keys.slice(start, start + 2), list_complete: start + 2 >= keys.length, cursor: String(start + 2) };
        }
    }};
    const request = (path, method = 'GET', body, key = 'test-secret') => worker.fetch(new Request('https://example.com/names/' + path, {
        method, headers: { 'X-API-Key': key }, body: body === undefined ? undefined : JSON.stringify(body)
    }), env);
    return { env, request };
}
test('authentication fails closed', async () => {
    const { env, request } = fixture();
    assert.equal((await request('a/g', 'GET', undefined, 'wrong')).status, 401);
    delete env.API_SECRET;
    assert.equal((await request('a/g')).status, 401);
});
test('optional labels round trip for encoded IPv6 CIDR and delete', async () => {
    const { request } = fixture();
    const path = 'a/g/' + encodeURIComponent('2001-db8--/64');
    assert.equal((await request(path, 'PUT', { name: ' Home ' })).status, 200);
    assert.deepEqual(await (await request('a/g')).json(), { '2001:db8::/64': 'Home' });
    assert.equal((await (await request(path)).json()).name, 'Home');
    assert.equal((await request(path, 'DELETE')).status, 200);
    assert.equal((await request(path)).status, 404);
});
test('list follows all pages and isolates groups', async () => {
    const { request } = fixture();
    for (let i = 0; i < 5; i++) await request('a/g/192_0_2_' + i, 'PUT', { name: 'Name ' + i });
    await request('a/other/192_0_2_0', 'PUT', { name: 'Other' });
    assert.equal(Object.keys(await (await request('a/g')).json()).length, 5);
});
test('invalid names and malformed path are rejected', async () => {
    const { request } = fixture();
    for (const name of ['', ' ', 7, 'x'.repeat(101)]) assert.equal((await request('a/g/ip', 'PUT', { name })).status, 400);
    assert.equal((await request('a/g/%ZZ')).status, 400);
});
