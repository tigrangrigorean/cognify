import test from 'node:test';
import assert from 'node:assert/strict';
import config from '../next.config.mjs';
test('Backend catch-all is fallback so it cannot bypass dynamic auth BFF',async()=>{
 const routes=await config.rewrites();
 assert.equal(Array.isArray(routes),false);
 assert.equal(routes.beforeFiles,undefined);
 assert.equal(routes.afterFiles,undefined);
 assert.equal(routes.fallback[0].source,'/api/:path*');
});
