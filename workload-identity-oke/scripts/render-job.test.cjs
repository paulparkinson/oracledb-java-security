const {test}=require('node:test');const assert=require('node:assert/strict');const {spawnSync}=require('node:child_process');
const base={PATH:process.env.PATH,OCI_REGION:'eu-frankfurt-1',DEMO_IMAGE:'registry.example/test@sha256:'+'a'.repeat(64)};
function run(mode,extra={}){return spawnSync(process.execPath,[__dirname+'/render-job.cjs',mode],{env:{...base,...extra},encoding:'utf8'});}
test('identity job is confined and unprivileged',()=>{const r=run('identity');assert.equal(r.status,0);const j=JSON.parse(r.stdout);assert.equal(j.metadata.namespace,'jdbc-workload-identity');assert.equal(j.spec.backoffLimit,0);assert.equal(j.spec.template.spec.serviceAccountName,'jdbc-allowed');assert.equal(j.spec.template.spec.containers[0].securityContext.readOnlyRootFilesystem,true);assert.equal(j.spec.template.spec.hostNetwork,undefined);});
test('denied service account remains distinct',()=>{assert.equal(JSON.parse(run('identity',{DEMO_SERVICE_ACCOUNT:'jdbc-denied'}).stdout).spec.template.spec.serviceAccountName,'jdbc-denied');});
test('mutable image tags rejected',()=>assert.notEqual(run('identity',{DEMO_IMAGE:'registry.example/test:latest'}).status,0));
test('unknown mode rejected',()=>assert.notEqual(run('auto').status,0));
test('arbitrary service account rejected',()=>assert.notEqual(run('identity',{DEMO_SERVICE_ACCOUNT:'default'}).status,0));
test('no inherited secret environment',()=>{const j=JSON.parse(run('identity',{DB_PASSWORD:'do-not-copy',OCI_CONFIG_FILE:'/secret'}).stdout);assert.ok(!j.spec.template.spec.containers[0].env.some(v=>/PASSWORD|CONFIG_FILE/.test(v.name)));});
test('unbounded duration rejected',()=>assert.notEqual(run('identity',{TEST_ROUNDS:'120',TEST_INTERVAL_SECONDS:'3600'}).status,0));
test('token requires exact target inputs',()=>assert.notEqual(run('token').status,0));
