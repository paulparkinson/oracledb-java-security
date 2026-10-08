// Generate-only evaluation alternative when a local container builder is unavailable.
// Builds public dependencies in a bounded, isolated Job; no registry credentials.
const fs=require('node:fs');const path=require('node:path');const cp=require('node:child_process');const crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
const built=cp.spawnSync(process.execPath,[path.join(__dirname,'render-job.cjs'),process.argv[2]],{env:process.env,encoding:'utf8'});
if(built.status!==0){process.stderr.write(built.stderr);process.exit(1);}
const job=JSON.parse(built.stdout),data={};
for(const [key,file] of Object.entries({'pom.xml':'pom.xml','Settings.java':'src/main/java/demo/Settings.java','WorkloadIdentityDemo.java':'src/main/java/demo/WorkloadIdentityDemo.java','SettingsTest.java':'src/test/java/demo/SettingsTest.java'}))data[key]=fs.readFileSync(path.join(root,file),'utf8');
const hash=crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
const name='jdbc-source-'+hash.slice(0,12);
const pod=job.spec.template.spec,c=pod.containers[0];
job.metadata.annotations={'evaluation.source.sha256':hash};
job.spec.activeDeadlineSeconds=Math.max(900,job.spec.activeDeadlineSeconds);
c.command=['/bin/sh','-ec'];
c.args=['mkdir -p /work/src/main/java/demo /work/src/test/java/demo; cp /source/pom.xml /work/; cp /source/Settings.java /source/WorkloadIdentityDemo.java /work/src/main/java/demo/; cp /source/SettingsTest.java /work/src/test/java/demo/; cd /work; mvn -B -ntp -Dmaven.repo.local=/work/.m2 -Duser.home=/work verify; exec java -cp "target/workload-identity-oke-0.1.0.jar:target/lib/*" demo.WorkloadIdentityDemo "$1"','source-job',process.argv[2]];
c.resources.limits={cpu:'1',memory:'768Mi'};
c.volumeMounts.push({name:'source',mountPath:'/source',readOnly:true},{name:'work',mountPath:'/work'});
pod.volumes.push({name:'source',configMap:{name}},{name:'work',emptyDir:{sizeLimit:'768Mi'}});
console.log(JSON.stringify({apiVersion:'v1',kind:'List',items:[{apiVersion:'v1',kind:'ConfigMap',metadata:{name,namespace:'jdbc-workload-identity',labels:{'app.kubernetes.io/part-of':'oracledb-java-security'}},immutable:true,data},job]},null,2));
