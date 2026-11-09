package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.policy.OperationMode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable, real PostgreSQL16 JDBC+three independently signed JWT proofs.
 * This is NOT an HTTP or real Identity/B62/mTLS deployment acceptance.
 */
class RuntimeVerifiedTaskSessionCreationJdbcTest {
    private static final String DB="jdbc:postgresql://127.0.0.1:5432/ownerlab_disposable";
    private static final String SCHEMA="ownerlab_gate_"+UUID.randomUUID().toString().replace("-","");
    private static final String ISS="https://identity.aetheris.example.invalid";
    private static final String AUD="aetheris-orchestrator-api";
    private static final String KID="create-session-review-key";
    private static final String FISS="https://fence.aetheris.example.invalid";
    private static final String FAUD="aetheris-runtime-fence-review";
    private static final String FKID="create-fence-review-key";
    private static final String WISS="https://workload.aetheris.example.invalid";
    private static final String WAUD="aetheris-runtime-workload-review";
    private static final String WKID="create-workload-review-key";
    private static final String ENDPOINT="review-primary";
    private static final String WORKER="runtime-review-worker-a";

    private static KeyPair identityKey, fenceKey, workloadKey;
    private static RuntimePinnedPrincipalVerifier verifier;
    private static RuntimePinnedLocalFenceLeaseVerifier fences;
    private static RuntimePinnedWorkloadIdentityVerifier workloads;
    private final RuntimeVerifiedTaskSessionCreationProbe probe =
        new RuntimeVerifiedTaskSessionCreationProbe();

    private record Identity(
        RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal,
        RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed) {}

    @BeforeAll
    static void fixture() throws Exception {
        Assumptions.assumeTrue("yes".equals(
            System.getenv("AETHERIS_SESSION_CREATE_JDBC_MUST_RUN")),
            "Only actual disposable PostgreSQL16 CI may run this class");
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("ownerlab_disposable",System.getenv("PGDATABASE"));
        assertEquals("ownerlab_ci",System.getenv("PGUSER"));
        assertNotNull(System.getenv("PGPASSWORD"));
        var maker=KeyPairGenerator.getInstance("RSA"); maker.initialize(2048);
        identityKey=maker.generateKeyPair();
        fenceKey=maker.generateKeyPair();
        workloadKey=maker.generateKeyPair();
        verifier=new RuntimePinnedPrincipalVerifier(
            pem(identityKey),KID,ISS,AUD,Clock.systemUTC());
        fences=new RuntimePinnedLocalFenceLeaseVerifier(
            pem(fenceKey),FKID,FISS,FAUD,Clock.systemUTC());
        workloads=new RuntimePinnedWorkloadIdentityVerifier(
            pem(workloadKey),WKID,WISS,WAUD,Clock.systemUTC());
        try(Connection admin=DriverManager.getConnection(
                DB,"ownerlab_ci",System.getenv("PGPASSWORD"));
            Statement sql=admin.createStatement()) {
            sql.execute("CREATE SCHEMA "+SCHEMA);
            sql.execute("SET search_path TO "+SCHEMA);
            sql.execute("""
                CREATE TABLE registry_tenants(
                    tenant_key varchar(96) PRIMARY KEY,status varchar(16) NOT NULL)
                """);
            sql.execute("""
                CREATE TABLE registry_projects(
                    tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
                    project_key varchar(96) NOT NULL,status varchar(16) NOT NULL,
                    PRIMARY KEY(tenant_key,project_key))
                """);
            sql.execute("""
                CREATE TABLE registry_tenant_memberships(
                    tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
                    identity_uid bigint NOT NULL, member_role varchar(16) NOT NULL,
                    status varchar(16) NOT NULL,revoked_at timestamptz,
                    valid_until timestamptz,
                    PRIMARY KEY(tenant_key,identity_uid))
                """);
            sql.execute("""
                CREATE TABLE registry_project_memberships(
                    tenant_key varchar(96) NOT NULL,project_key varchar(96) NOT NULL,
                    identity_uid bigint NOT NULL,member_role varchar(16) NOT NULL,
                    status varchar(16) NOT NULL,revoked_at timestamptz,
                    valid_until timestamptz,
                    PRIMARY KEY(tenant_key,project_key,identity_uid),
                    FOREIGN KEY(tenant_key,project_key)
                      REFERENCES registry_projects(tenant_key,project_key),
                    FOREIGN KEY(tenant_key,identity_uid)
                      REFERENCES registry_tenant_memberships(tenant_key,identity_uid))
                """);
            sql.execute("""
                CREATE TABLE b62_local_resource_fence(
                    resource_key varchar(32) PRIMARY KEY, endpoint varchar(96),
                    term bigint NOT NULL,state varchar(16) NOT NULL,
                    expires_at timestamptz NOT NULL)
                """);
            sql.execute("""
                CREATE TABLE synthetic_session_registration(
                    issuer varchar(255) NOT NULL,kid varchar(64) NOT NULL,
                    jti varchar(128) NOT NULL,uid bigint NOT NULL,
                    iat_seconds bigint NOT NULL,state varchar(16) NOT NULL,
                    PRIMARY KEY(issuer,kid,jti))
                """);
            sql.execute("""
                CREATE TABLE signer_key_states(
                    issuer varchar(255) NOT NULL,kid varchar(64) NOT NULL,
                    state varchar(16) NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),
                    PRIMARY KEY(issuer,kid))
                """);
            sql.execute("""
                CREATE TABLE principal_epochs(
                    principal_id bigint PRIMARY KEY,
                    reject_issued_at_or_before_epoch bigint NOT NULL)
                """);
            sql.execute("""
                CREATE TABLE aetheris_tasks(
                    id uuid PRIMARY KEY,title varchar(160) NOT NULL,
                    command_text varchar(8000) NOT NULL,
                    state varchar(32) NOT NULL,mode varchar(32) NOT NULL,
                    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
                    version bigint NOT NULL,
                    owner_identity_uid bigint,owner_tenant_key varchar(96),
                    owner_project_key varchar(96),owner_verified_at timestamptz,
                    CHECK (
                      (owner_identity_uid IS NULL AND owner_tenant_key IS NULL
                        AND owner_project_key IS NULL AND owner_verified_at IS NULL)
                      OR
                      (owner_identity_uid>0 AND owner_tenant_key IS NOT NULL
                        AND owner_project_key IS NOT NULL AND owner_verified_at IS NOT NULL)
                    ),
                    FOREIGN KEY(owner_tenant_key,owner_project_key,owner_identity_uid)
                      REFERENCES registry_project_memberships(
                        tenant_key,project_key,identity_uid))
                """);
            sql.execute("""
                CREATE TABLE aetheris_task_events(
                    id uuid PRIMARY KEY,
                    task_id uuid NOT NULL REFERENCES aetheris_tasks(id),
                    event_at timestamptz NOT NULL,
                    state varchar(32) NOT NULL,
                    message varchar(8000) NOT NULL,
                    metadata_json varchar(16000) NOT NULL)
                """);
            sql.execute("""
                INSERT INTO registry_tenants VALUES
                    ('tenant-alpha','ACTIVE'),('tenant-beta','ACTIVE')
                """);
            sql.execute("""
                INSERT INTO registry_projects VALUES
                    ('tenant-alpha','red','ACTIVE'),('tenant-beta','red','ACTIVE'),
                    ('tenant-alpha','blue','ACTIVE'),('tenant-alpha','blocked','SUSPENDED')
                """);
            sql.execute("""
                INSERT INTO registry_tenant_memberships VALUES
                    ('tenant-alpha',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                    ('tenant-beta',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                    ('tenant-alpha',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day')
                """);
            sql.execute("""
                INSERT INTO registry_project_memberships VALUES
                    ('tenant-alpha','red',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
                    ('tenant-beta','red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                    ('tenant-alpha','blue',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
                    ('tenant-alpha','blocked',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day')
                """);
            sql.execute("""
                INSERT INTO b62_local_resource_fence VALUES
                    ('runtime','review-primary',7,'ACTIVE',now()+interval '1 day')
                """);
            sql.execute("""
                INSERT INTO signer_key_states VALUES
                    ('https://identity.aetheris.example.invalid','create-session-review-key','ACTIVE')
                """);
            sql.execute("""
                INSERT INTO principal_epochs VALUES (42,0),(77,0)
                """);
        }
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(
                pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
    }

    private static Connection transaction() throws SQLException {
        Connection c=DriverManager.getConnection(
            DB,"ownerlab_ci",System.getenv("PGPASSWORD"));
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try(Statement s=c.createStatement()) {
            s.execute("SET search_path TO "+SCHEMA);
        }
        c.setAutoCommit(false);
        return c;
    }

    private static Identity identity(long uid, String scope) {
        Instant now=Instant.now();
        String jwt=Jwts.builder().header().keyId(KID).and()
            .issuer(ISS).subject("synthetic-"+uid+"@example.invalid")
            .claim("aud",List.of(AUD)).claim("uid",uid)
            .claim("role","DEVELOPER").claim("scopes",List.of(scope))
            .id("ownerlab_"+UUID.randomUUID())
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(identityKey.getPrivate(),Jwts.SIG.RS256).compact();
        var p=verifier.verifyBearerForRevocation("Bearer "+jwt);
        return new Identity(p,p.requireRevocationFacts());
    }

    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease(String worker) {
        Instant now=Instant.now();
        String token=Jwts.builder().header().keyId(FKID).and()
            .subject("synthetic-resource-leader").issuer(FISS)
            .claim("aud",List.of(FAUD))
            .claim("purpose","runtime-local-fence-review")
            .claim("resource","runtime").claim("endpoint",ENDPOINT)
            .claim("term",7).claim("workload",worker)
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(65)))
            .signWith(fenceKey.getPrivate(),Jwts.SIG.RS256).compact();
        return fences.verifySignedLease(token);
    }

    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload(String worker) {
        Instant now=Instant.now();
        String token=Jwts.builder().header().keyId(WKID).and()
            .subject(worker).issuer(WISS).claim("aud",List.of(WAUD))
            .claim("purpose","runtime-workload-review").claim("resource","runtime")
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(65)))
            .signWith(workloadKey.getPrivate(),Jwts.SIG.RS256).compact();
        return workloads.verifySignedWorkload(token);
    }

    private static void register(Connection c, Identity identity) throws SQLException {
        var s=identity.signed();
        try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO synthetic_session_registration
            (issuer,kid,jti,uid,iat_seconds,state)
            VALUES (?,?,?,?,?,'ACTIVE')
            """)) {
            p.setString(1,s.verifiedIssuer());p.setString(2,s.verifiedKeyId());
            p.setString(3,s.signedTokenId());p.setLong(4,s.signedIdentityUid());
            p.setLong(5,s.signedIssuedAt().getEpochSecond());
            assertEquals(1,p.executeUpdate());
        }
    }

    private UUID create(Connection c, Identity id, String tenant,String project)
            throws SQLException {
        return probe.createSynthetic(c,id.principal(),tenant,project,
            "synthetic guarded task","No execution, only verified SQL write",
            OperationMode.PRIVATE,lease(WORKER),workload(WORKER));
    }

    private static long count(Connection c) throws SQLException {
        try(Statement s=c.createStatement();var r=s.executeQuery(
            "SELECT count(*) FROM aetheris_tasks")) {
            assertTrue(r.next());return r.getLong(1);
        }
    }

    @Test
    void signedSessionAndBoundFenceCreatesOnlyInExactMembership() throws Exception {
        try(Connection c=transaction()) {
            var a=identity(42,"orchestrator:write");
            var b=identity(77,"orchestrator:write");
            register(c,a);register(c,b);
            long before=count(c);
            UUID one=create(c,a,"tenant-alpha","red");
            UUID two=create(c,a,"tenant-beta","red");
            UUID three=create(c,b,"tenant-alpha","blue");
            assertNotEquals(one,two);assertNotEquals(one,three);
            assertEquals(before+3,count(c));
            try(var s=c.prepareStatement("""
                SELECT owner_identity_uid,owner_tenant_key,owner_project_key,
                       owner_verified_at FROM aetheris_tasks WHERE id=?""")) {
                s.setObject(1,one);
                try(var row=s.executeQuery()) {
                    assertTrue(row.next());assertEquals(42L,row.getLong(1));
                    assertEquals("tenant-alpha",row.getString(2));
                    assertEquals("red",row.getString(3));
                    assertNotNull(row.getTimestamp(4));
                }
            }
            c.rollback();
        }
    }

    @Test
    void noSessionOrWrongTenantProjectOrSuspensionDeniesWithoutInsert()
            throws Exception {
        try(Connection c=transaction()) {
            long before=count(c);
            var a=identity(42,"orchestrator:write");
            assertThrows(SecurityException.class,
                ()->create(c,a,"tenant-alpha","red"));
            register(c,a);
            assertThrows(SecurityException.class,
                ()->create(c,a,"tenant-alpha","blue"));
            assertThrows(SecurityException.class,
                ()->create(c,a,"tenant-alpha","blocked"));
            assertThrows(SecurityException.class,
                ()->create(c,a,"nonexistent","red"));
            assertEquals(before,count(c));
            c.rollback();
        }
    }

    @Test
    void revokedSessionAndBlockedSigningKeyAndPrincipalEpochDeny()
            throws Exception {
        try(Connection c=transaction()) {
            long before=count(c);
            var id=identity(42,"orchestrator:write");register(c,id);
            try(var stmt=c.prepareStatement("""
                UPDATE synthetic_session_registration SET state='REVOKED'
                  WHERE issuer=? AND kid=? AND jti=?""")) {
                stmt.setString(1,ISS);stmt.setString(2,KID);
                stmt.setString(3,id.signed().signedTokenId());
                assertEquals(1,stmt.executeUpdate());
            }
            assertThrows(SecurityException.class,
                ()->create(c,id,"tenant-alpha","red"));
            c.rollback();
        }
        try(Connection c=transaction()) {
            long before=count(c);
            var id=identity(42,"orchestrator:write");register(c,id);
            try(var s=c.prepareStatement("""
                UPDATE signer_key_states SET state='REVOKED'
                  WHERE issuer=? AND kid=?""")) {
                s.setString(1,ISS);s.setString(2,KID);
                assertEquals(1,s.executeUpdate());
            }
            assertThrows(SecurityException.class,
                ()->create(c,id,"tenant-alpha","red"));
            assertEquals(before,count(c));c.rollback();
        }
        try(Connection c=transaction()) {
            var id=identity(42,"orchestrator:write");register(c,id);
            try(var s=c.prepareStatement("""
                UPDATE principal_epochs SET reject_issued_at_or_before_epoch=?
                  WHERE principal_id=?""")) {
                s.setLong(1,id.signed().signedIssuedAt().getEpochSecond());
                s.setLong(2,42L);
                assertEquals(1,s.executeUpdate());
            }
            assertThrows(SecurityException.class,
                ()->create(c,id,"tenant-alpha","red"));
            c.rollback();
        }
    }

    @Test
    void workloadMismatchAndFenceRevocationDeny() throws Exception {
        try(Connection c=transaction()) {
            var id=identity(42,"orchestrator:write");register(c,id);
            long before=count(c);
            assertThrows(SecurityException.class,()->probe.createSynthetic(
                c,id.principal(),"tenant-alpha","red","name","safe command",
                OperationMode.PRIVATE,lease(WORKER),workload("runtime-review-worker-b")));
            try(Statement s=c.createStatement()) {
                s.execute("UPDATE b62_local_resource_fence SET state='CLOSED'");
            }
            assertThrows(SecurityException.class,
                ()->create(c,id,"tenant-alpha","red"));
            assertEquals(before,count(c));
            c.rollback();
        }
    }

    @Test
    void readOnlyScopeAndTransactionSettingsAreRejected() throws Exception {
        try(Connection c=transaction()) {
            var readonly=identity(42,"orchestrator:read");register(c,readonly);
            assertThrows(SecurityException.class,
                ()->create(c,readonly,"tenant-alpha","red"));
            // PostgreSQL disallows changing transaction isolation once the
            // transaction has executed a statement. Start a clean transaction
            // before checking that the probe itself denies REPEATABLE_READ.
            c.rollback();
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            var write=identity(42,"orchestrator:write");
            assertThrows(IllegalStateException.class,
                ()->create(c,write,"tenant-alpha","red"));
            c.rollback();
        }
    }

    @Test
    void signedSessionRevokeBlocksUntilCommittedCreateThenDeniesNext()
            throws Exception {
        var id=identity(42,"orchestrator:write");
        try(Connection writer=transaction();Connection revoker=transaction()) {
            register(writer,id);writer.commit();
            UUID created=create(writer,id,"tenant-alpha","red");
            try(Statement s=revoker.createStatement()) {
                s.execute("SET LOCAL statement_timeout = '750ms'");
            }
            SQLException blocked=assertThrows(SQLException.class,()->{
                try(var update=revoker.prepareStatement("""
                    UPDATE synthetic_session_registration SET state='REVOKED'
                    WHERE issuer=? AND kid=? AND jti=?""")) {
                    update.setString(1,ISS);update.setString(2,KID);
                    update.setString(3,id.signed().signedTokenId());
                    update.executeUpdate();
                }
            });
            assertEquals("57014",blocked.getSQLState());
            revoker.rollback();writer.commit();
            try(var update=revoker.prepareStatement("""
                UPDATE synthetic_session_registration SET state='REVOKED'
                WHERE issuer=? AND kid=? AND jti=?""")) {
                update.setString(1,ISS);update.setString(2,KID);
                update.setString(3,id.signed().signedTokenId());
                assertEquals(1,update.executeUpdate());
            }
            revoker.commit();
            assertThrows(SecurityException.class,
                ()->create(writer,id,"tenant-alpha","red"));
            writer.rollback();
            try(var stmt=revoker.prepareStatement("""
                SELECT owner_identity_uid FROM aetheris_tasks WHERE id=?""")) {
                stmt.setObject(1,created);
                try(var rows=stmt.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(42L,rows.getLong(1));
                }
            }
            revoker.rollback();
        }
    }

    @Test
    void missingSignerOrEpochAuthorityRowsDenyByDefault() throws Exception {
        // Absent authority records are NOT equivalent to an unrevoked token.
        // All deletes are rolled back; CI never alters an external registry.
        try(Connection c=transaction()) {
            var signed=identity(42,"orchestrator:write");
            register(c,signed);
            try(var q=c.prepareStatement("""
                    DELETE FROM signer_key_states WHERE issuer=? AND kid=?""")) {
                q.setString(1,ISS);q.setString(2,KID);
                assertEquals(1,q.executeUpdate());
            }
            assertThrows(SecurityException.class,
                ()->create(c,signed,"tenant-alpha","red"));
            c.rollback();
        }
        try(Connection c=transaction()) {
            var signed=identity(42,"orchestrator:write");
            register(c,signed);
            try(var q=c.prepareStatement("""
                    DELETE FROM principal_epochs WHERE principal_id=?""")) {
                q.setLong(1,42L);
                assertEquals(1,q.executeUpdate());
            }
            assertThrows(SecurityException.class,
                ()->create(c,signed,"tenant-alpha","red"));
            c.rollback();
        }
    }

    @Test
    void signerAndPrincipalEpochRevocationUpdatesSerializeWithTaskCreate()
            throws Exception {
        // A WRITE committed before a REVOKE is allowed; after the revoke
        // commits the same signed principal cannot write again.
        // Critically, the revoke UPDATE cannot commit while task insertion
        // holds the two authority rows (previous NOT EXISTS could not lock).
        var signed=identity(42,"orchestrator:write");
        try(Connection writer=transaction();Connection revoker=transaction()) {
            register(writer,signed);
            writer.commit();
            UUID task=create(writer,signed,"tenant-alpha","red");
            assertNotNull(task);

            try(Statement stmt=revoker.createStatement()) {
                stmt.execute("SET LOCAL statement_timeout = '750ms'");
            }
            SQLException blockedSigner=assertThrows(SQLException.class,()->{
                try(var q=revoker.prepareStatement("""
                     UPDATE signer_key_states SET state='REVOKED'
                       WHERE issuer=? AND kid=?""")) {
                    q.setString(1,ISS);q.setString(2,KID);
                    q.executeUpdate();
                }
            });
            assertEquals("57014",blockedSigner.getSQLState());
            revoker.rollback();

            try(Statement stmt=revoker.createStatement()) {
                stmt.execute("SET LOCAL statement_timeout = '750ms'");
            }
            SQLException blockedEpoch=assertThrows(SQLException.class,()->{
                try(var q=revoker.prepareStatement("""
                     UPDATE principal_epochs
                        SET reject_issued_at_or_before_epoch=?
                       WHERE principal_id=?""")) {
                    q.setLong(1,signed.signed().signedIssuedAt().getEpochSecond());
                    q.setLong(2,42L);
                    q.executeUpdate();
                }
            });
            assertEquals("57014",blockedEpoch.getSQLState());
            revoker.rollback();

            writer.commit();
            try(var q=revoker.prepareStatement("""
                    UPDATE signer_key_states SET state='REVOKED'
                      WHERE issuer=? AND kid=?""")) {
                q.setString(1,ISS);q.setString(2,KID);
                assertEquals(1,q.executeUpdate());
            }
            try(var q=revoker.prepareStatement("""
                    UPDATE principal_epochs
                       SET reject_issued_at_or_before_epoch=?
                     WHERE principal_id=?""")) {
                q.setLong(1,signed.signed().signedIssuedAt().getEpochSecond());
                q.setLong(2,42L);
                assertEquals(1,q.executeUpdate());
            }
            revoker.commit();

            assertThrows(SecurityException.class,
                ()->create(writer,signed,"tenant-alpha","red"));
            writer.rollback();
        } finally {
            // Restore only these disposable test-only authority rows so the
            // fixture remains order-independent across JUnit test methods.
            try(Connection restorer=transaction();
                Statement q=restorer.createStatement()) {
                q.execute("UPDATE signer_key_states SET state='ACTIVE'");
                q.execute("UPDATE principal_epochs SET reject_issued_at_or_before_epoch=0");
                restorer.commit();
            }
        }
    }

    private final RuntimeVerifiedTaskReadProbe reader =
        new RuntimeVerifiedTaskReadProbe();

    private static UUID event(Connection c, UUID task, Instant timestamp)
            throws SQLException {
        UUID id=UUID.randomUUID();
        try(var q=c.prepareStatement("""
            INSERT INTO aetheris_task_events
                (id,task_id,event_at,state,message,metadata_json)
            VALUES (?,?,?,'QUEUED','private message','{"secret":"never-return"}')
            """)) {
            q.setObject(1,id);q.setObject(2,task);
            q.setTimestamp(3,java.sql.Timestamp.from(timestamp));
            assertEquals(1,q.executeUpdate());
        }
        return id;
    }

    @Test
    void sessionBoundReadsIsolateSameProjectOwnersAndPaginateEventHeaders()
            throws Exception {
        try(Connection c=transaction()) {
            // Two independent verified signed identities share ONE project.
            try(Statement q=c.createStatement()) {
                q.execute("""
                    INSERT INTO registry_project_memberships
                    VALUES ('tenant-alpha','red',77,'MEMBER','ACTIVE',NULL,
                            now()+interval '1 day')
                    """);
            }
            var aliceWrite=identity(42,"orchestrator:write");
            var aliceRead=identity(42,"orchestrator:read");
            var bobWrite=identity(77,"orchestrator:write");
            var bobRead=identity(77,"orchestrator:read");
            for(var id:List.of(aliceWrite,aliceRead,bobWrite,bobRead))
                register(c,id);
            UUID alice=create(c,aliceWrite,"tenant-alpha","red");
            UUID aliceOtherTenant=create(c,aliceWrite,"tenant-beta","red");
            UUID bob=create(c,bobWrite,"tenant-alpha","red");
            UUID ownerless=UUID.randomUUID();
            try(var q=c.prepareStatement("""
                INSERT INTO aetheris_tasks
                (id,title,command_text,state,mode,created_at,updated_at,version)
                VALUES (?,'legacy ownerless','no execution','QUEUED','PRIVATE',
                        now(),now(),0)
                """)) {
                q.setObject(1,ownerless);
                assertEquals(1,q.executeUpdate());
            }
            Instant now=Instant.now();
            UUID older=event(c,alice,now.minusSeconds(12));
            UUID newer=event(c,alice,now.minusSeconds(6));
            event(c,bob,now.minusSeconds(3));
            var lease=lease(WORKER);
            var workload=workload(WORKER);

            assertEquals(alice,reader.findOne(c,aliceRead.principal(),
                "tenant-alpha","red",alice,lease,workload).orElseThrow().id());
            assertTrue(reader.findOne(c,bobRead.principal(),
                "tenant-alpha","red",alice,lease,workload).isEmpty());
            assertTrue(reader.findOne(c,aliceRead.principal(),
                "tenant-alpha","red",bob,lease,workload).isEmpty());
            assertTrue(reader.findOne(c,aliceRead.principal(),
                "tenant-beta","red",alice,lease,workload).isEmpty());
            assertTrue(reader.findOne(c,aliceRead.principal(),
                "tenant-alpha","red",aliceOtherTenant,lease,workload).isEmpty());
            assertTrue(reader.findOne(c,aliceRead.principal(),
                "tenant-alpha","red",ownerless,lease,workload).isEmpty());

            var recent=reader.recent(c,aliceRead.principal(),
                "tenant-alpha","red",50,lease,workload);
            assertTrue(recent.stream().anyMatch(t->t.id().equals(alice)));
            assertTrue(recent.stream().noneMatch(t->t.id().equals(bob)
                || t.id().equals(aliceOtherTenant) || t.id().equals(ownerless)));

            var page1=reader.history(c,aliceRead.principal(),
                "tenant-alpha","red",alice,null,null,1,lease,workload);
            assertEquals(1,page1.size());assertEquals(newer,page1.get(0).eventId());
            var page2=reader.history(c,aliceRead.principal(),
                "tenant-alpha","red",alice,page1.get(0).eventAt(),
                page1.get(0).eventId(),1,lease,workload);
            assertEquals(1,page2.size());assertEquals(older,page2.get(0).eventId());
            assertTrue(reader.history(c,aliceRead.principal(),
                "tenant-alpha","red",alice,page2.get(0).eventAt(),
                page2.get(0).eventId(),1,lease,workload).isEmpty());
            assertTrue(reader.history(c,bobRead.principal(),
                "tenant-alpha","red",alice,null,null,100,lease,workload).isEmpty());
            assertFalse(page1.toString().contains("never-return"));
            assertFalse(recent.toString().contains("no execution"));
            c.rollback();
        }
    }

    @Test
    void readsDenyMissingOrRevokedSessionSignerEpochAndMembership()
            throws Exception {
        try(Connection c=transaction()) {
            var write=identity(42,"orchestrator:write");
            var read=identity(42,"orchestrator:read");
            register(c,write);
            UUID task=create(c,write,"tenant-alpha","red");
            var lease=lease(WORKER);
            var workload=workload(WORKER);
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            register(c,read);
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isPresent());
            assertThrows(SecurityException.class,()->reader.findOne(
                c,write.principal(),"tenant-alpha","red",task,lease,workload));
            assertThrows(SecurityException.class,()->reader.findOne(
                c,read.principal(),"tenant-alpha","red",task,lease,
                workload("runtime-review-worker-b")));
            assertThrows(IllegalArgumentException.class,()->reader.history(
                c,read.principal(),"tenant-alpha","red",task,Instant.now(),
                null,5,lease,workload));

            try(var q=c.prepareStatement("""
                UPDATE synthetic_session_registration SET state='REVOKED'
                 WHERE issuer=? AND kid=? AND jti=?
                """)) {
                q.setString(1,ISS);q.setString(2,KID);
                q.setString(3,read.signed().signedTokenId());
                assertEquals(1,q.executeUpdate());
            }
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            try(var q=c.prepareStatement("""
                UPDATE synthetic_session_registration SET state='ACTIVE'
                 WHERE issuer=? AND kid=? AND jti=?
                """)) {
                q.setString(1,ISS);q.setString(2,KID);
                q.setString(3,read.signed().signedTokenId());q.executeUpdate();
            }
            try(Statement q=c.createStatement()) {
                q.execute("UPDATE signer_key_states SET state='REVOKED'");
            }
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            try(Statement q=c.createStatement()) {
                q.execute("UPDATE signer_key_states SET state='ACTIVE'");
            }
            try(var q=c.prepareStatement("""
                UPDATE principal_epochs
                   SET reject_issued_at_or_before_epoch=?
                 WHERE principal_id=42
                """)) {
                q.setLong(1,read.signed().signedIssuedAt().getEpochSecond());
                assertEquals(1,q.executeUpdate());
            }
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            try(Statement q=c.createStatement()) {
                q.execute("UPDATE principal_epochs SET reject_issued_at_or_before_epoch=0");
            }
            try(Statement q=c.createStatement()) {
                q.execute("""
                    UPDATE registry_project_memberships SET status='REVOKED'
                    WHERE tenant_key='tenant-alpha' AND project_key='red'
                      AND identity_uid=42
                    """);
            }
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            // VIEWER read grants are not approved by the security contract.
            // Restore ACTIVE status, then prove the unapproved role still DENIES.
            try(Statement q=c.createStatement()) {
                q.execute("""
                    UPDATE registry_project_memberships
                    SET status='ACTIVE',member_role='VIEWER'
                    WHERE tenant_key='tenant-alpha' AND project_key='red'
                      AND identity_uid=42
                    """);
            }
            assertTrue(reader.findOne(c,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            c.rollback();
        }
    }

    @Test
    void authorizedReadLocksSessionAgainstConcurrentRevocation()
            throws Exception {
        var write=identity(42,"orchestrator:write");
        var read=identity(42,"orchestrator:read");
        try(Connection setup=transaction();Connection readerTx=transaction();
                Connection revoker=transaction()) {
            register(setup,write);register(setup,read);
            UUID task=create(setup,write,"tenant-alpha","red");
            setup.commit();
            var lease=lease(WORKER);
            var workload=workload(WORKER);
            assertTrue(reader.findOne(readerTx,read.principal(),
                "tenant-alpha","red",task,lease,workload).isPresent());
            try(Statement q=revoker.createStatement()) {
                q.execute("SET LOCAL statement_timeout='750ms'");
            }
            SQLException blocked=assertThrows(SQLException.class,()->{
                try(var q=revoker.prepareStatement("""
                    UPDATE synthetic_session_registration SET state='REVOKED'
                     WHERE issuer=? AND kid=? AND jti=?
                    """)) {
                    q.setString(1,ISS);q.setString(2,KID);
                    q.setString(3,read.signed().signedTokenId());
                    q.executeUpdate();
                }
            });
            assertEquals("57014",blocked.getSQLState());
            revoker.rollback();
            readerTx.commit();
            try(var q=revoker.prepareStatement("""
                UPDATE synthetic_session_registration SET state='REVOKED'
                 WHERE issuer=? AND kid=? AND jti=?
                """)) {
                q.setString(1,ISS);q.setString(2,KID);
                q.setString(3,read.signed().signedTokenId());
                assertEquals(1,q.executeUpdate());
            }
            revoker.commit();
            assertTrue(reader.findOne(readerTx,read.principal(),
                "tenant-alpha","red",task,lease,workload).isEmpty());
            readerTx.rollback();
            try(var q=setup.prepareStatement(
                    "DELETE FROM aetheris_tasks WHERE id=?")) {
                q.setObject(1,task);assertEquals(1,q.executeUpdate());
            }
            setup.commit();
        }
    }

    @AfterAll
    static void dropFixture() throws Exception {
        if(identityKey==null)return;
        try(Connection admin=DriverManager.getConnection(
                DB,"ownerlab_ci",System.getenv("PGPASSWORD"));
            Statement sql=admin.createStatement()) {
            sql.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
            try(var check=sql.executeQuery(
                "SELECT count(*) FROM pg_namespace WHERE nspname='"+SCHEMA+"'")) {
                assertTrue(check.next());assertEquals(0,check.getLong(1));
            }
        }
        System.out.println("RUNTIME_THREE_SIGNER_SESSION_FENCED_TASK_CREATE_PG16_CLEANUP_PASS");
    }
}
