// Bind KV as kv_cfupdater; store API_SECRET as an encrypted secret.
// Optional expiry cron: CF_ACCESS_TOKEN secret, CF_ACCOUNT_ID, CF_GROUP_ID,
// and a */5 * * * * schedule. Never expire metadata using KV TTL.
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
const prefixFor = (account, group) => `name:${account}:${group}:`;
function decode(raw) {
    if (raw === null) return null;
    try { const value = JSON.parse(raw); if (value && value.schema === 2) return value; } catch { }
    const match = /^(.*)DELAdded:([0-9]+)$/.exec(raw);
    const dateOnly = /^Added:([0-9]+)$/.exec(raw);
    return { schema: 2, name: match ? match[1] : dateOnly ? '' : raw,
        addedAt: match ? Number(match[2]) : dateOnly ? Number(dateOnly[1]) : null,
        expiresAt: null, deleted: false, updatedAt: 0 };
}
async function get(env, key) { return decode(await env.kv_cfupdater.get(key)); }
async function put(env, key, record) {
    await env.kv_cfupdater.put(key, JSON.stringify(record), { metadata: { record } });
}
async function list(env, prefix) {
    const result = {}; let cursor;
    do {
        const page = await env.kv_cfupdater.list({ prefix, cursor });
        for (const key of page.keys) {
            const record = key.metadata?.record || await get(env, key.name);
            if (record) result[key.name.slice(prefix.length)] = record;
        }
        if (page.list_complete) break;
        cursor = page.cursor;
    } while (cursor);
    return result;
}
function validTime(value) { return value === null || (Number.isSafeInteger(value) && value >= 0); }
function canonical(ip) {
    try {
        const [address, suffix] = ip.split('/');
        let bits, value;
        if (address.includes(':')) {
            const host = new URL('http://[' + address + ']').hostname.slice(1,-1);
            const halves=host.split('::');
            const left=halves[0]?halves[0].split(':'):[];
            const right=halves.length>1 && halves[1]?halves[1].split(':'):[];
            const words=halves.length===1?left:[...left,...Array(8-left.length-right.length).fill('0'),...right];
            value=words.reduce((v,w)=>(v<<16n)|BigInt('0x'+w),0n);bits=128;
        } else {
            const octets=address.split('.');if(octets.length!==4) return ip;
            value=octets.reduce((v,o)=>(v<<8n)|BigInt(o),0n);bits=32;
        }
        const prefix=suffix===undefined?bits:Number(suffix);
        if(!Number.isInteger(prefix)||prefix<0||prefix>bits)return ip;
        const shift=BigInt(bits-prefix);return bits+':'+((value>>shift)<<shift).toString(16)+'/'+prefix;
    } catch {return ip;}
}
export default {
    async fetch(request, env) {
        if (!env.API_SECRET || request.headers.get('X-API-Key') !== env.API_SECRET) return json({ error: 'Unauthorized' }, 401);
        try {
            const parts = new URL(request.url).pathname.split('/').filter(Boolean);
            if (!['names', 'records'].includes(parts[0]) || ![3,4].includes(parts.length)) return json({error:'Not found'},404);
            const [route,account,group,encoded] = parts;
            if (!/^[a-zA-Z0-9-]+$/.test(account) || !/^[a-zA-Z0-9-]+$/.test(group)) return json({error:'Invalid scope'},400);
            const prefix = prefixFor(account,group);
            if (parts.length === 3) {
                if (request.method !== 'GET') return json({error:'Method not allowed'},405);
                const records = await list(env,prefix);
                return json(route === 'records' ? records : Object.fromEntries(Object.entries(records).filter(([,r])=>!r.deleted && r.name).map(([ip,r])=>[ip,r.name])));
            }
            let ip;
            try { ip=decodeURIComponent(encoded).replace(/_/g,'.').replace(/-/g,':'); }
            catch { return json({error:'Invalid IP path'},400); }
            const key=prefix+ip;
            const existing=await get(env,key);
            if(request.method==='GET') {
                if (!existing || existing.deleted) return json({name:null},404);
                return json(route==='records'?existing:{name:existing.name});
            }
            if(request.method==='DELETE') {
                // Tombstones prevent a stale phone resurrecting metadata after a list refresh.
                await put(env,key,{schema:2,name:'',addedAt:existing?.addedAt??null,expiresAt:null,deleted:true,updatedAt:Date.now()});
                return json({message:'Name deleted successfully'});
            }
            if(request.method!=='PUT') return json({error:'Method not allowed'},405);
            let body; try {body=await request.json();} catch {return json({error:'Invalid JSON'},400);}
            if(!body || typeof body.name!=='string' || body.name.trim().length>100 || (route==='names' && !body.name.trim())) return json({error:'Invalid name'},400);
            let record;
            if(route==='records') {
                if(!validTime(body.addedAt??null) || !validTime(body.expiresAt??null) || !validTime(body.updatedAt) || body.updatedAt===null || typeof body.deleted!=='boolean') return json({error:'Invalid record'},400);
                record={schema:2,name:body.name.trim(),addedAt:body.addedAt??null,expiresAt:body.deleted?null:body.expiresAt??null,deleted:body.deleted,updatedAt:body.updatedAt};
            } else record={schema:2,name:body.name.trim(),addedAt:existing?.addedAt??null,expiresAt:existing?.expiresAt??null,deleted:false,updatedAt:Date.now()};
            // Sequential stale retries must not overwrite a newer edit or expiry deletion.
            if(existing && existing.updatedAt > record.updatedAt) return json(existing);
            await put(env,key,record); return json(record);
        } catch {return json({error:'KV operation failed'},500);}
    },
    async scheduled(event, env) {
        if(!env.CF_ACCESS_TOKEN || !env.CF_ACCOUNT_ID || !env.CF_GROUP_ID) return;
        if(!/^[a-fA-F0-9]{32}$/.test(env.CF_ACCOUNT_ID) || !/^[a-fA-F0-9-]{32,36}$/.test(env.CF_GROUP_ID)) throw new Error('Invalid cleanup scope');
        const prefix=prefixFor(env.CF_ACCOUNT_ID,env.CF_GROUP_ID);
        const records=await list(env,prefix);
        const due=Object.entries(records).filter(([,r])=>!r.deleted && r.expiresAt!==null && r.expiresAt<=Date.now());
        if(!due.length)return;
        const url=`https://api.cloudflare.com/client/v4/accounts/${env.CF_ACCOUNT_ID}/access/groups/${env.CF_GROUP_ID}`;
        const headers={'Authorization':`Bearer ${env.CF_ACCESS_TOKEN}`,'Content-Type':'application/json'};
        const response=await fetch(url,{headers});
        if(!response.ok)throw new Error('Access read failed');
        const body=await response.json();
        if(!body.success || !Array.isArray(body.result?.include))throw new Error('Invalid Access response');
        // Recheck each record before revocation in case expiry was extended during the scan.
        const confirmed=[];
        for(const [ip,r] of due){const latest=await get(env,prefix+ip);if(latest && !latest.deleted && latest.updatedAt===r.updatedAt && latest.expiresAt===r.expiresAt)confirmed.push([ip,r]);}
        const expired=new Set(confirmed.map(([ip])=>canonical(ip)));
        const group=body.result;
        const include=group.include.filter(rule=>!expired.has(canonical(rule.ip?.ip)));
        if(include.length!==group.include.length){
            const payload={};for(const field of ['name','exclude','require','is_default'])if(field in group)payload[field]=group[field];payload.include=include;
            const update=await fetch(url,{method:'PUT',headers,body:JSON.stringify(payload)});
            if(!update.ok || !(await update.json()).success)throw new Error('Access update failed');
        }
        for(const [ip,r] of confirmed)await put(env,prefix+ip,{...r,name:'',deleted:true,expiresAt:null,updatedAt:Date.now()});
    }
};
