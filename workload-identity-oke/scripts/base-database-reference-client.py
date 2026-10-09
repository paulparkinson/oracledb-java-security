"""Isolated BaseDB workload/control comparison; never substitutes a principal."""
import base64, datetime, json, os, ssl, sys, time
import oci, oracledb
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

stage='configuration'
def emit(**fields):
 print(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),**fields)),flush=True)

try:
 mode=sys.argv[1] if len(sys.argv)>1 else 'missing'
 if mode not in ('workload','control'):raise ValueError('Explicit workload or control mode required')
 if not os.environ.get('DB_DSN','').startswith('tcps://'):raise ValueError('TCPS EZConnect+ required')
 if any(os.getenv(k) for k in ('DB_PASSWORD','DB_USERNAME','OCI_CONFIG_FILE','OCI_CLI_AUTH')):
  raise ValueError('Alternate credentials prohibited')
 if mode=='workload':
  stage='oke-identity'
  signer=oci.auth.signers.get_oke_workload_identity_resource_principal_signer()
  emit(stage=stage,result='PASS')
  client=oci.identity_data_plane.DataplaneClient({'region':os.environ['OCI_REGION']},signer=signer,
   timeout=(10,20),retry_strategy=oci.retry.NoneRetryStrategy())
  key=rsa.generate_private_key(public_exponent=65537,key_size=4096)
  public=key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()
  private=key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()).decode()
  scope='urn:oracle:db::id::'+os.environ['OCI_COMPARTMENT_ID']+'::'+os.environ['OCI_DATABASE_ID']
  stage='database-token'
  token=client.generate_scoped_access_token(oci.identity_data_plane.models.GenerateScopedAccessTokenDetails(scope=scope,public_key=public)).data.token
  expected=os.environ['EXPECTED_WORKLOAD_SUBJECT'];schema='OKE_JDBC_DEMO'
 else:
  # Operator acquires a distinct database-scoped control token outside the pod.
  # Its API signing key is never uploaded. Control credentials arrive only on stdin.
  data=json.load(sys.stdin);token=data['token'];private=data['privateKey'];expected=data['subject'];schema='IAM_CONTROL_DEMO'
  emit(stage='operator-control',result='SELECTED',notWorkloadIdentity=True)
 part=token.split('.')[1]
 claims=json.loads(base64.urlsafe_b64decode(part+'='*(-len(part)%4)))
 if claims.get('sub')!=expected or claims.get('exp',0)<time.time()+120:raise ValueError('Token subject/lifetime mismatch')
 emit(stage='database-token',result='PASS',mode=mode,subjectMatchesMapping=True)
 # Verify certificate trust AND hostname against the private diagnostic CA.
 context=ssl.create_default_context(cafile='/public-ca/server-cert.pem')
 stage='database-login'
 with oracledb.connect(dsn=os.environ['DB_DSN'],access_token=(token,private),ssl_context=context,
   ssl_server_dn_match=True,tcp_connect_timeout=15,retry_count=0) as connection:
  connection.call_timeout=20000
  with connection.cursor() as cursor:
   cursor.execute("select sys_context('USERENV','SESSION_USER'),sys_context('USERENV','AUTHENTICATION_METHOD') from dual")
   row=cursor.fetchone()
   if row!=(schema,'TOKEN_GLOBAL'):raise ValueError('Unexpected session identity')
   emit(stage=stage,result='PASS',mode=mode,sessionUser=row[0],authentication=row[1])
except Exception as error:
 fields=dict(stage=stage,result='FAIL',mode=mode,exception=type(error).__name__)
 if isinstance(error,oracledb.Error):
  fields.update(oracleError=error.args[0].code,fullCode=error.args[0].full_code)
 if isinstance(error,oci.exceptions.ServiceError): fields.update(httpStatus=error.status,serviceCode=error.code)
 emit(**fields);sys.exit(1)
