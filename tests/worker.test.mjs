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
    const records = (path, method = 'GET', body) => worker.fetch(new Request('https://example.com/records/' + path, {method, headers:{'X-API-Key':'test-secret'}, body:body===undefined?undefined:JSON.stringify(body)}),env);
    return { env, request, records, values };
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

test('records retain added and expiry dates, and reject invalid dates', async () => {
    const {records} = fixture();
    const value={name:'Office',addedAt:1000,expiresAt:9000,deleted:false,updatedAt:2000};
    assert.equal((await records('a/g/192_0_2_1%2F32','PUT',value)).status,200);
    const read=await (await records('a/g')).json();
    assert.equal(read['192.0.2.1/32'].addedAt,1000);
    assert.equal(read['192.0.2.1/32'].expiresAt,9000);
    assert.equal((await records('a/g/ip','PUT',{...value,expiresAt:'tomorrow'})).status,400);
});
test('old string names migrate without inventing dates', async () => {
    const {records,values}=fixture();
    values.set('name:a:g:192.0.2.1',{value:'HomeDELAdded:1234'});
    values.set('name:a:g:192.0.2.2',{value:'Work'});
    const read=await (await records('a/g')).json();
    assert.equal(read['192.0.2.1'].addedAt,1234);
    assert.equal(read['192.0.2.2'].addedAt,null);
});
test('stale retries cannot overwrite a newer tombstone', async () => {
    const {records}=fixture();
    const value={name:'',addedAt:1000,expiresAt:null,deleted:true,updatedAt:5000};
    await records('a/g/ip','PUT',value);
    await records('a/g/ip','PUT',{...value,name:'Stale',deleted:false,updatedAt:2000});
    assert.equal((await (await records('a/g')).json()).ip.deleted,true);
});
test('scheduled cleanup preserves non-IP rules and metadata on Access failure', async (t) => {
    const {env,records,values}=fixture();
    env.CF_ACCOUNT_ID='a'.repeat(32);env.CF_GROUP_ID='b'.repeat(32);env.CF_ACCESS_TOKEN='scoped-token';
    const path=env.CF_ACCOUNT_ID+'/'+env.CF_GROUP_ID;
    const expired={name:'Expired',addedAt:1,expiresAt:2,deleted:false,updatedAt:3};
    await records(path+'/192_0_2_1%2F32','PUT',expired);
    const group={name:'Group',include:[{ip:{ip:'192.0.2.1'}},{email:{email:'keep@example.com'}},{ip:{ip:'192.0.2.2/32'}}],exclude:[{everyone:{}}],require:[]};
    let update;
    t.mock.method(globalThis,'fetch',async (url,options)=>{
        if(options.method==='PUT'){update=JSON.parse(options.body);return new Response('{}',{status:503});}
        return new Response(JSON.stringify({success:true,result:group}));
    });
    await assert.rejects(()=>worker.scheduled({},env));
    assert.deepEqual(update.include,[{email:{email:'keep@example.com'}},{ip:{ip:'192.0.2.2/32'}}]);
    assert.deepEqual(update.exclude,group.exclude);
    assert.equal((await (await records(path)).json())['192.0.2.1/32'].deleted,false);
    t.mock.restoreAll();
    t.mock.method(globalThis,'fetch',async (url,options)=>new Response(JSON.stringify(options.method==='PUT'?{success:true}:{success:true,result:group})));
    await worker.scheduled({},env);
    assert.equal((await (await records(path)).json())['192.0.2.1/32'].deleted,true);
});
