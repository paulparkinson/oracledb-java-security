"""Independent Oracle Python driver comparison; credentials never leave memory."""
import base64
import datetime
import json
import os
import sys
import time
import oci
import oracledb
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

stage = "configuration"
def event(**fields):
    print(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(), **fields)), flush=True)

try:
    for name in ("DB_PASSWORD", "DB_USERNAME", "OCI_CONFIG_FILE", "OCI_CLI_AUTH"):
        if os.getenv(name):
            raise ValueError("Alternate credential configuration is not allowed")
    event(stage=stage, driver=oracledb.__version__, sdk=oci.__version__, thin=oracledb.is_thin_mode())
    stage = "oke-identity"
    signer = oci.auth.signers.get_oke_workload_identity_resource_principal_signer()
    event(stage=stage, result="PASS")
    client = oci.identity_data_plane.DataplaneClient(
        {"region": os.environ["OCI_REGION"]}, signer=signer,
        timeout=(10, 20), retry_strategy=oci.retry.NoneRetryStrategy())
    stage = "database-token"
    # Same documented PoP request flow as Oracle's oci_tokens plugin, but an
    # explicit OKE signer instead of the plugin's generic resource principal.
    key = rsa.generate_private_key(public_exponent=65537, key_size=4096)
    public_key = key.public_key().public_bytes(serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    private_key = key.private_bytes(serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
    scope = "urn:oracle:db::id::" + os.environ["OCI_COMPARTMENT_ID"] + "::" + os.environ["OCI_DATABASE_ID"]
    token = client.generate_scoped_access_token(
        oci.identity_data_plane.models.GenerateScopedAccessTokenDetails(
            scope=scope, public_key=public_key)).data.token
    part = token.split(".")[1]
    claims = json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
    if claims.get("sub") != os.environ["EXPECTED_WORKLOAD_SUBJECT"]:
        raise ValueError("Workload subject mismatch")
    if claims.get("exp", 0) <= time.time():
        raise ValueError("Expired token")
    event(stage=stage, result="PASS", subjectMatchesExistingMapping=True)
    stage = "python-login"
    with oracledb.connect(dsn=os.environ["DB_DSN"], access_token=(token, private_key),
            ssl_server_dn_match=True, tcp_connect_timeout=15, retry_count=0) as connection:
        connection.call_timeout = 15000
        with connection.cursor() as cursor:
            cursor.execute("select sys_context('USERENV','SESSION_USER'), sys_context('USERENV','AUTHENTICATION_METHOD'), systimestamp from dual")
            row = cursor.fetchone()
            if row[0] != "OKE_JDBC_DEMO":
                raise ValueError("Unexpected database mapping")
            event(stage=stage, result="PASS", sessionUser=row[0], authentication=row[1], databaseTime=str(row[2]))
except Exception as error:
    details = dict(stage=stage, result="FAIL", exception=type(error).__name__)
    if isinstance(error, oracledb.Error) and error.args:
        details["oracleError"] = error.args[0].code
        details["fullCode"] = error.args[0].full_code
    if isinstance(error, oci.exceptions.ServiceError):
        details["httpStatus"] = error.status
        details["serviceCode"] = error.code
    event(**details)
    sys.exit(1)
