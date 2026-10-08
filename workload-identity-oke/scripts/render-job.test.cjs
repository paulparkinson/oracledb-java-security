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
const jdbc={OCI_COMPARTMENT_ID:'example',OCI_DATABASE_ID:'example',DB_JDBC_URL:'example',EXPECTED_DB_USER:'OKE_JDBC_DEMO'};
test('default JDBC has no wallet mount',()=>{const j=JSON.parse(run('jdbc',jdbc).stdout);assert.ok(!j.spec.template.spec.volumes.some(v=>v.secret));});
test('optional wallet exposes only a read-only certificate file',()=>{const r=run('jdbc',{...jdbc,DEMO_USE_MTLS_WALLET:'true',OCI_DB_ADMIN_PASSWORD:'never-copy'});assert.equal(r.status,0);const p=JSON.parse(r.stdout).spec.template.spec;assert.deepEqual(p.volumes.find(v=>v.name==='oracle-wallet').secret,{secretName:'jdbc-mtls-wallet',defaultMode:0o440,items:[{key:'cwallet.sso',path:'cwallet.sso'}]});assert.equal(p.containers[0].volumeMounts.find(v=>v.name==='oracle-wallet').readOnly,true);assert.ok(!r.stdout.includes('never-copy'));});
test('wallet cannot be mounted for token or identity modes',()=>{for(const m of ['identity','token'])assert.notEqual(run(m,{...jdbc,DEMO_USE_MTLS_WALLET:'true'}).status,0);});
test('wallet flag rejects ambiguous values',()=>assert.notEqual(run('jdbc',{...jdbc,DEMO_USE_MTLS_WALLET:'yes'}).status,0));
