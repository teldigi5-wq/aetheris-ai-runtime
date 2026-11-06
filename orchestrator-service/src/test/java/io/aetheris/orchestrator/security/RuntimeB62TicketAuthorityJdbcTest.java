package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable PostgreSQL16 security-boundary candidate, never production API.
 * No raw table grants for the caller; separate ticket issuer cannot mutate
 * effect rows; limited NOLOGIN function owner alone mediates the insert.
 *
 * Ticket issuer is an explicitly trusted test actor assumed to verify
 * principal/workload and approve a one-time request BEFORE issuance.
 * A DB worker credential alone cannot authorize or mint such a request.
 * This DOES NOT prove independent certificate custody or physical fencing.
 */
class RuntimeB62TicketAuthorityJdbcTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String WORKLOAD_ISSUER = "https://workload.aetheris.example.invalid";
    private static final String FENCE_ISSUER = "https://fence.aetheris.example.invalid";
    private static final UUID ALICE_TASK = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB_TASK = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OWNERLESS_TASK = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final SecureRandom RNG = new SecureRandom();
    private static String schema, ownerPassword;
    private static String workerRole, workerPassword, otherWorkerRole, otherWorkerPassword;
    private static String issuerRole, issuerPassword, guardRole;
    private static String cancelRole, cancelPassword;
    private static KeyPair personKey, workloadKey, fenceKey;
    private static RuntimePinnedPrincipalVerifier principalVerifier;
    private static RuntimePinnedWorkloadIdentityVerifier workloadVerifier;
    private static RuntimePinnedLocalFenceLeaseVerifier fenceVerifier;
    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal alice, bob;
    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload;
    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease lease;

    private static String secret() {
        byte[] bytes = new byte[24];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static Connection db(String user, String password) throws SQLException {
        Connection c = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:5432/b62_effect_disposable", user, password);
        if (schema != null) c.setSchema(schema);
        return c;
    }
    private static Connection owner() throws SQLException { return db("b62_effect_owner", ownerPassword); }
    private static Connection worker() throws SQLException { return db(workerRole, workerPassword); }
    private static Connection workerB() throws SQLException { return db(otherWorkerRole, otherWorkerPassword); }
    private static Connection ticketIssuer() throws SQLException { return db(issuerRole, issuerPassword); }
    private static Connection canceller() throws SQLException { return db(cancelRole, cancelPassword); }

    private static RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal(long uid, List<String> scopes) {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("ticket-identity-key").and()
            .issuer(ISSUER).subject("synthetic-" + uid + "@example.invalid")
            .claim("aud", List.of("runtime-effect-review"))
            .claim("uid", uid).claim("role", "DEVELOPER")
            .claim("scopes", scopes).id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(180)))
            .signWith(personKey.getPrivate(), Jwts.SIG.RS256).compact();
        return principalVerifier.verifyBearerForRevocation("Bearer " + jwt);
    }

    private static RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload verifiedWorkload() {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("ticket-workload-key").and()
            .issuer(WORKLOAD_ISSUER).subject("synthetic-worker")
            .claim("aud", List.of("runtime-workload")).claim("purpose", "runtime-workload-review")
            .claim("resource", "runtime")
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
            .signWith(workloadKey.getPrivate(), Jwts.SIG.RS256).compact();
        return workloadVerifier.verifySignedWorkload(jwt);
    }

    private static RuntimePinnedLocalFenceLeaseVerifier.VerifiedLease verifiedLease() {
        Instant issued = Instant.now().minusSeconds(8);
        String jwt = Jwts.builder().header().keyId("ticket-fence-key").and()
            .issuer(FENCE_ISSUER).claim("aud", List.of("runtime-fence"))
            .claim("purpose", "runtime-local-fence-review").claim("resource", "runtime")
            .claim("endpoint", "synthetic-primary").claim("term", 2)
            .claim("workload", "synthetic-worker")
            .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
            .signWith(fenceKey.getPrivate(), Jwts.SIG.RS256).compact();
        return fenceVerifier.verifySignedLease(jwt);
    }

    private static void register(Connection admin,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal person) throws SQLException {
        var facts = person.requireRevocationFacts();
        try (PreparedStatement p = admin.prepareStatement(
            "INSERT INTO synthetic_session_registration(issuer,kid,jti,uid,iat_seconds,state) "
            + "VALUES (?,?,?,?,?,'ACTIVE')")) {
            p.setString(1, facts.verifiedIssuer());
            p.setString(2, facts.verifiedKeyId());
            p.setString(3, facts.signedTokenId());
            p.setLong(4, facts.signedIdentityUid());
            p.setLong(5, facts.signedIssuedAt().getEpochSecond());
            assertEquals(1, p.executeUpdate());
        }
        // This preview's trusted fixture registrar inserts a DB scope fact
        // ONLY after the independently signed JWT has been verified above.
        // Neither the ticket issuer nor either worker role has DML rights on
        // these proof rows. This is not a production identity enrollment flow.
        try (PreparedStatement grant = admin.prepareStatement(
                "INSERT INTO b62_verified_scope_facts(issuer,kid,jti,uid,scope_name) "
                  + "VALUES (?,?,?,?,?)")) {
            for (String scope : List.of("orchestrator:read", "orchestrator:write")) {
                if (!person.hasScope(scope)) continue;
                grant.setString(1, facts.verifiedIssuer());
                grant.setString(2, facts.verifiedKeyId());
                grant.setString(3, facts.signedTokenId());
                grant.setLong(4, facts.signedIdentityUid());
                grant.setString(5, scope);
                assertEquals(1, grant.executeUpdate());
            }
        }
    }

    // An authorized test owner models an independently trusted request
    // registrar. This fixture cannot certify an actual remote approval flow.
    private static void approveRequest(String role,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal person,
            String tenant, String project, UUID taskId, UUID effectId, int ttlSeconds)
            throws SQLException {
        var facts = person.requireRevocationFacts();
        try (Connection c = owner(); PreparedStatement stmt = c.prepareStatement(
              "INSERT INTO b62_approved_effect_requests(effect_id,issuer_role,"
              + "worker_role,task_id,uid,tenant_key,project_key,issuer,kid,jti,"
              + "iat_seconds,endpoint,term,workload_subject,state,expires_at)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'APPROVED',"
              + "clock_timestamp()+(? * interval '1 second'))")) {
            stmt.setObject(1, effectId);
            stmt.setString(2, issuerRole);
            stmt.setString(3, role);
            stmt.setObject(4, taskId);
            stmt.setLong(5, facts.signedIdentityUid());
            stmt.setString(6, tenant);
            stmt.setString(7, project);
            stmt.setString(8, facts.verifiedIssuer());
            stmt.setString(9, facts.verifiedKeyId());
            stmt.setString(10, facts.signedTokenId());
            stmt.setLong(11, facts.signedIssuedAt().getEpochSecond());
            stmt.setString(12, lease.endpoint());
            stmt.setLong(13, lease.term());
            stmt.setString(14, workload.subject());
            stmt.setInt(15, ttlSeconds);
            assertEquals(1, stmt.executeUpdate());
        }
    }

    private static void approvedIssue(String role,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal person,
            String tenant, String project, UUID taskId, UUID effectId, int ttlSeconds)
            throws SQLException {
        // Existing positive/negative tests explicitly use the trusted
        // fixture registrar before invoking the separate issuer login.
        approveRequest(role, person, tenant, project, taskId, effectId,
            Math.max(ttlSeconds, 0) + 15);
        issue(role, person, tenant, project, taskId, effectId, ttlSeconds);
    }

    // Issuer has INSERT only and no effect DML. Actual authenticated issuer
    // implementation/cert custody is deliberately OUTSIDE this local fixture.
    private static void issue(String boundRole, RuntimePinnedPrincipalVerifier.VerifiedPrincipal person,
            String tenant, String project, UUID taskId, UUID effectId, int ttlSeconds)
            throws SQLException {
        var facts = person.requireRevocationFacts();
        try (Connection c = ticketIssuer();
             PreparedStatement stmt = c.prepareStatement(
              "INSERT INTO b62_effect_tickets(ticket_id,requestor_role,task_id,uid,tenant_key,"
              + "project_key,issuer,kid,jti,iat_seconds,endpoint,term,workload_subject,state,expires_at) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ISSUED',clock_timestamp()+(? * interval '1 second'))")) {
            stmt.setObject(1, effectId);
            stmt.setString(2, boundRole);
            stmt.setObject(3, taskId);
            stmt.setLong(4, facts.signedIdentityUid());
            stmt.setString(5, tenant);
            stmt.setString(6, project);
            stmt.setString(7, facts.verifiedIssuer());
            stmt.setString(8, facts.verifiedKeyId());
            stmt.setString(9, facts.signedTokenId());
            stmt.setLong(10, facts.signedIssuedAt().getEpochSecond());
            stmt.setString(11, lease.endpoint());
            stmt.setLong(12, lease.term());
            stmt.setString(13, workload.subject());
            stmt.setInt(14, ttlSeconds);
            assertEquals(1, stmt.executeUpdate());
        }
    }

    private static boolean effect(Connection c,
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal person,
            String tenant, String project, UUID taskId, UUID effectId) throws SQLException {
        return new RuntimeSyntheticTicketEffectProbe().recordEffectSynthetic(
            c, person, tenant, project, taskId, effectId, lease, workload);
    }

    private static long countEffects() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement();
             var rows = s.executeQuery("SELECT count(*) FROM b62_guarded_effects")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static String state(UUID ticket) throws SQLException {
        try (Connection c = owner(); PreparedStatement s =
                 c.prepareStatement("SELECT state FROM b62_effect_tickets WHERE ticket_id=?")) {
            s.setObject(1, ticket);
            try (var r = s.executeQuery()) {
                assertTrue(r.next());
                return r.getString(1);
            }
        }
    }


    private static int cancelApproval(UUID id) throws SQLException {
        try (Connection c = canceller(); PreparedStatement p = c.prepareStatement(
                "UPDATE b62_approved_effect_requests SET state='CANCELED' "
                  + "WHERE effect_id=? AND state='APPROVED'")) {
            p.setObject(1,id);
            return p.executeUpdate();
        }
    }

    private static int cancelTicket(UUID id) throws SQLException {
        try (Connection c = canceller(); PreparedStatement p = c.prepareStatement(
                "UPDATE b62_effect_tickets SET state='CANCELED' "
                  + "WHERE ticket_id=? AND state='ISSUED'")) {
            p.setObject(1,id);
            return p.executeUpdate();
        }
    }

    private static String approvalState(UUID id) throws SQLException {
        try (Connection c = owner(); PreparedStatement p = c.prepareStatement(
                "SELECT state FROM b62_approved_effect_requests WHERE effect_id=?")) {
            p.setObject(1,id);
            try (var rows = p.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    @BeforeAll
    static void disposableCIOnly() throws Exception {
        assertEquals("yes", System.getenv("AETHERIS_B62_FULL_EFFECT_JDBC_MUST_RUN"));
        assertEquals("yes", System.getenv("AETHERIS_B62_EFFECT_DISPOSABLE_CI_ONLY"));
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("127.0.0.1", System.getenv("PGHOST"));
        assertEquals("b62_effect_disposable", System.getenv("PGDATABASE"));
        assertEquals("b62_effect_owner", System.getenv("PGUSER"));
        ownerPassword = System.getenv("PGPASSWORD");
        assertNotNull(ownerPassword);
        assertFalse(ownerPassword.isBlank());

        var keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        personKey = keygen.generateKeyPair();
        workloadKey = keygen.generateKeyPair();
        fenceKey = keygen.generateKeyPair();
        Clock clock = Clock.systemUTC();
        principalVerifier = new RuntimePinnedPrincipalVerifier(
            pem(personKey), "ticket-identity-key", ISSUER, "runtime-effect-review", clock);
        workloadVerifier = new RuntimePinnedWorkloadIdentityVerifier(
            pem(workloadKey), "ticket-workload-key", WORKLOAD_ISSUER, "runtime-workload", clock);
        fenceVerifier = new RuntimePinnedLocalFenceLeaseVerifier(
            pem(fenceKey), "ticket-fence-key", FENCE_ISSUER, "runtime-fence", clock);

        String nonce = UUID.randomUUID().toString().replace("-", "");
        schema = "b62_ticket_" + nonce;
        String suffix = nonce.substring(0, 20);
        workerRole = "b62_ticket_worker_" + suffix;
        otherWorkerRole = "b62_ticket_other_" + suffix;
        issuerRole = "b62_ticket_issuer_" + suffix;
        guardRole = "b62_ticket_guard_" + suffix;
        cancelRole = "b62_ticket_cancel_" + suffix;
        workerPassword = secret();
        otherWorkerPassword = secret();
        issuerPassword = secret();
        cancelPassword = secret();
        for (String role : List.of(workerRole, otherWorkerRole, issuerRole, guardRole, cancelRole)) {
            assertTrue(role.matches("b62_ticket_[a-z]+_[0-9a-f]{20}"));
        }
        assertTrue(schema.matches("b62_ticket_[0-9a-f]{32}"));

        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE " + workerRole + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + workerPassword + "'");
            s.execute("CREATE ROLE " + otherWorkerRole + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + otherWorkerPassword + "'");
            s.execute("CREATE ROLE " + issuerRole + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + issuerPassword + "'");
            s.execute("CREATE ROLE " + cancelRole + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + cancelPassword + "'");
            s.execute("CREATE ROLE " + guardRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            s.execute("CREATE SCHEMA " + schema);
            s.execute("SET search_path TO " + schema + ", pg_catalog");
            s.execute("CREATE TABLE b62_local_resource_fence(resource_key text PRIMARY KEY, endpoint text NOT NULL,term bigint NOT NULL,state text NOT NULL,expires_at timestamptz NOT NULL,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE aetheris_tasks(id uuid PRIMARY KEY,owner_identity_uid bigint,owner_tenant_key text,owner_project_key text,owner_verified_at timestamptz,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE tenant_memberships(tenant_key text,identity_uid bigint,status text,revoked_at timestamptz,valid_until timestamptz,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0),PRIMARY KEY(tenant_key,identity_uid))");
            s.execute("CREATE TABLE project_memberships(tenant_key text,project_key text,identity_uid bigint,status text,revoked_at timestamptz,valid_until timestamptz,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0),PRIMARY KEY(tenant_key,project_key,identity_uid))");
            s.execute("CREATE TABLE synthetic_session_registration(issuer text,kid text,jti text,uid bigint,iat_seconds bigint,state text,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0),PRIMARY KEY(issuer,kid,jti))");
            s.execute("CREATE TABLE signer_authorizations(issuer text,kid text,state text NOT NULL,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0),PRIMARY KEY(issuer,kid))");
            s.execute("CREATE TABLE principal_epochs(principal_id bigint PRIMARY KEY,reject_issued_at_or_before_epoch bigint NOT NULL,lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0))");
            s.execute("CREATE TABLE b62_verified_scope_facts(issuer text NOT NULL,kid text NOT NULL,jti text NOT NULL,uid bigint NOT NULL,scope_name text NOT NULL CHECK(scope_name IN ('orchestrator:read','orchestrator:write')),PRIMARY KEY(issuer,kid,jti,uid,scope_name))");
            s.execute("CREATE TABLE b62_issuer_role_bindings(issuer_role text PRIMARY KEY,worker_role text NOT NULL,state text NOT NULL CHECK(state IN ('ACTIVE','SUSPENDED')))");
            s.execute("INSERT INTO b62_issuer_role_bindings VALUES ('" + issuerRole + "','" + workerRole + "','ACTIVE')");
            s.execute("CREATE TABLE b62_guarded_effects(effect_id uuid PRIMARY KEY,task_id uuid NOT NULL,fence_term bigint NOT NULL,workload_subject text NOT NULL)");
            s.execute("CREATE TABLE b62_approved_effect_requests(effect_id uuid PRIMARY KEY,issuer_role text NOT NULL,worker_role text NOT NULL,task_id uuid NOT NULL,uid bigint NOT NULL,tenant_key text NOT NULL,project_key text NOT NULL,issuer text NOT NULL,kid text NOT NULL,jti text NOT NULL,iat_seconds bigint NOT NULL,endpoint text NOT NULL,term bigint NOT NULL,workload_subject text NOT NULL,state text NOT NULL CHECK(state IN ('APPROVED','ISSUED','CANCELED')),expires_at timestamptz NOT NULL)");
            s.execute("CREATE TABLE b62_effect_tickets(ticket_id uuid PRIMARY KEY,requestor_role text NOT NULL,task_id uuid NOT NULL,uid bigint NOT NULL,tenant_key text NOT NULL,project_key text NOT NULL,issuer text NOT NULL,kid text NOT NULL,jti text NOT NULL,iat_seconds bigint NOT NULL,endpoint text NOT NULL,term bigint NOT NULL,workload_subject text NOT NULL,state text NOT NULL CHECK(state IN ('ISSUED','CONSUMED','CANCELED')),expires_at timestamptz NOT NULL)");
            s.execute("REVOKE CREATE ON SCHEMA " + schema + " FROM PUBLIC");
            s.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + workerRole + "," + otherWorkerRole + "," + issuerRole + "," + guardRole + "," + cancelRole);
            s.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO " + guardRole);
            s.execute("GRANT UPDATE(lock_guard) ON b62_local_resource_fence,aetheris_tasks,tenant_memberships,project_memberships,synthetic_session_registration,signer_authorizations,principal_epochs TO " + guardRole);
            s.execute("GRANT UPDATE(state) ON b62_effect_tickets,b62_approved_effect_requests TO " + guardRole);
            s.execute("GRANT INSERT ON b62_guarded_effects TO " + guardRole);
            s.execute("GRANT INSERT ON b62_effect_tickets TO " + issuerRole);
            s.execute("GRANT SELECT(effect_id,state), UPDATE(state) ON b62_approved_effect_requests TO " + cancelRole);
            s.execute("GRANT SELECT(ticket_id,state), UPDATE(state) ON b62_effect_tickets TO " + cancelRole);
        }

        // Owned by a NOLOGIN role with only narrow table grants. Untrusted
        // caller cannot change search_path or replace the trusted schema.
        // Create+REVOKE PUBLIC+GRANT in one transaction (no exposure window).
        String tag = String.valueOf((char)36) + "b62_ticket" + (char)36;
        String ddl = """
            CREATE FUNCTION %s.b62_record_ticket_effect(
              p_endpoint text,p_term bigint,p_task uuid,p_uid bigint,
              p_tenant text,p_project text,p_issuer text,p_kid text,
              p_jti text,p_iat bigint,p_effect uuid,p_workload text)
            RETURNS boolean LANGUAGE sql VOLATILE SECURITY DEFINER
            SET search_path = %s, pg_temp AS %s
            WITH chosen_ticket AS MATERIALIZED (
              SELECT ticket.ticket_id
              FROM b62_effect_tickets ticket
              WHERE ticket.ticket_id=p_effect
                AND ticket.requestor_role=session_user::text
                AND ticket.task_id=p_task AND ticket.uid=p_uid
                AND ticket.tenant_key=p_tenant AND ticket.project_key=p_project
                AND ticket.issuer=p_issuer AND ticket.kid=p_kid
                AND ticket.jti=p_jti AND ticket.iat_seconds=p_iat
                AND ticket.endpoint=p_endpoint AND ticket.term=p_term
                AND ticket.workload_subject=p_workload
                AND ticket.state='ISSUED'
                AND ticket.expires_at>clock_timestamp()
              FOR UPDATE OF ticket
            ),
            locked_fence AS MATERIALIZED (
              SELECT fence.term
              FROM b62_local_resource_fence fence
              WHERE fence.resource_key='runtime'
                AND fence.endpoint=p_endpoint AND fence.term=p_term
                AND fence.state='ACTIVE' AND fence.expires_at>clock_timestamp()
                AND current_setting('transaction_isolation')='read committed'
                AND NOT pg_is_in_recovery()
              FOR SHARE OF fence
            ),
            scoped AS MATERIALIZED (
              SELECT t.id AS task_id,fence.term AS fence_term
              FROM locked_fence fence
              CROSS JOIN aetheris_tasks t
              JOIN tenant_memberships tm
                ON tm.tenant_key=t.owner_tenant_key AND tm.identity_uid=t.owner_identity_uid
              JOIN project_memberships pm
                ON pm.tenant_key=t.owner_tenant_key
               AND pm.project_key=t.owner_project_key AND pm.identity_uid=t.owner_identity_uid
              JOIN synthetic_session_registration active ON active.uid=t.owner_identity_uid
              JOIN signer_authorizations signer
                ON signer.issuer=active.issuer AND signer.kid=active.kid
              JOIN principal_epochs epoch ON epoch.principal_id=active.uid
              WHERE t.id=p_task AND t.owner_identity_uid=p_uid
                AND t.owner_tenant_key=p_tenant AND t.owner_project_key=p_project
                AND t.owner_verified_at IS NOT NULL
                AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
                AND (tm.valid_until IS NULL OR tm.valid_until>clock_timestamp())
                AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
                AND (pm.valid_until IS NULL OR pm.valid_until>clock_timestamp())
                AND active.issuer=p_issuer AND active.kid=p_kid
                AND active.jti=p_jti AND active.uid=p_uid
                AND active.iat_seconds=p_iat AND active.state='ACTIVE'
                AND signer.state='ACTIVE' AND epoch.reject_issued_at_or_before_epoch<p_iat
              FOR SHARE OF t,tm,pm,active,signer,epoch
            ),
            inserted AS (
              INSERT INTO b62_guarded_effects(effect_id,task_id,fence_term,workload_subject)
              SELECT p_effect,scoped.task_id,scoped.fence_term,p_workload
              FROM chosen_ticket CROSS JOIN scoped
              ON CONFLICT (effect_id) DO NOTHING
              RETURNING effect_id
            ),
            consumed AS (
              UPDATE b62_effect_tickets ticket SET state='CONSUMED'
              FROM inserted
              WHERE ticket.ticket_id=inserted.effect_id AND ticket.state='ISSUED'
              RETURNING ticket.ticket_id
            )
            SELECT EXISTS(SELECT 1 FROM consumed)
            %s;
            """.formatted(schema, schema, tag, tag);
        // Independent SQL enforcement of the minimum issuance policy.
        // The untrusted INSERT-only issuer cannot write authenticated scope
        // facts or grant itself additional worker identities. This remains
        // a synthetic registrar, not an authenticated production issuer.
        String issuerPolicy = """
            CREATE FUNCTION %s.b62_check_ticket_issuance()
            RETURNS trigger LANGUAGE plpgsql VOLATILE SECURITY DEFINER
            SET search_path = %s, pg_temp AS %s
            BEGIN
              IF NOT EXISTS (
                SELECT 1
                FROM b62_issuer_role_bindings binding
                JOIN synthetic_session_registration active
                  ON active.issuer=NEW.issuer AND active.kid=NEW.kid
                 AND active.jti=NEW.jti AND active.uid=NEW.uid
                 AND active.iat_seconds=NEW.iat_seconds AND active.state='ACTIVE'
                JOIN signer_authorizations signer
                  ON signer.issuer=active.issuer AND signer.kid=active.kid
                 AND signer.state='ACTIVE'
                JOIN principal_epochs epoch
                  ON epoch.principal_id=NEW.uid
                 AND epoch.reject_issued_at_or_before_epoch<NEW.iat_seconds
                WHERE binding.issuer_role=session_user::text
                  AND binding.worker_role=NEW.requestor_role
                  AND binding.state='ACTIVE'
                  AND EXISTS (
                    SELECT 1 FROM b62_verified_scope_facts proof
                    WHERE proof.issuer=NEW.issuer AND proof.kid=NEW.kid
                      AND proof.jti=NEW.jti AND proof.uid=NEW.uid
                      AND proof.scope_name='orchestrator:read'
                  )
                  AND EXISTS (
                    SELECT 1 FROM b62_verified_scope_facts proof
                    WHERE proof.issuer=NEW.issuer AND proof.kid=NEW.kid
                      AND proof.jti=NEW.jti AND proof.uid=NEW.uid
                      AND proof.scope_name='orchestrator:write'
                  )
              ) THEN
                RAISE EXCEPTION 'B62_TICKET_ISSUER_POLICY_DENY'
                  USING ERRCODE='42501';
              END IF;
              -- A distinct registrar must pre-approve this *exact*
              -- effect. The untrusted issuer has no rights on approval rows.
              -- Lock/spend the approval with an atomic UPDATE; on failure
              -- the whole ticket INSERT statement rolls back.
              UPDATE b62_approved_effect_requests approval
                 SET state='ISSUED'
               WHERE approval.effect_id=NEW.ticket_id
                 AND approval.issuer_role=session_user::text
                 AND approval.worker_role=NEW.requestor_role
                 AND approval.task_id=NEW.task_id
                 AND approval.uid=NEW.uid
                 AND approval.tenant_key=NEW.tenant_key
                 AND approval.project_key=NEW.project_key
                 AND approval.issuer=NEW.issuer
                 AND approval.kid=NEW.kid
                 AND approval.jti=NEW.jti
                 AND approval.iat_seconds=NEW.iat_seconds
                 AND approval.endpoint=NEW.endpoint
                 AND approval.term=NEW.term
                 AND approval.workload_subject=NEW.workload_subject
                 AND approval.state='APPROVED'
                 AND approval.expires_at>clock_timestamp()
                 AND NEW.expires_at<=approval.expires_at;
              IF NOT FOUND THEN
                RAISE EXCEPTION 'B62_EFFECT_REQUEST_NOT_APPROVED'
                    USING ERRCODE='42501';
              END IF;
              RETURN NEW;
            END;
            %s;
            """.formatted(schema, schema, tag, tag);
        // Review-only monotonic transition guards. Effective role changes
        // while the ticket/issuance SECURITY DEFINER methods execute; the
        // original session role must independently match the permitted actor.
        // These do not protect against a privileged PostgreSQL owner.
        String transitionTag = "$b62_transition$";
        String approvalFlow = """
            CREATE FUNCTION %s.b62_approval_transition()
            RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
            SET search_path=pg_catalog AS %s
            BEGIN
              IF OLD.state='APPROVED' AND NEW.state='ISSUED'
                 AND current_user='%s' AND session_user='%s' THEN
                RETURN NEW;
              END IF;
              IF OLD.state='APPROVED' AND NEW.state='CANCELED'
                 AND current_user='%s' AND session_user='%s' THEN
                RETURN NEW;
              END IF;
              RAISE EXCEPTION 'B62_APPROVAL_TRANSITION_DENIED' USING ERRCODE='42501';
            END;
            %s;
            """.formatted(schema,transitionTag,guardRole,issuerRole,
                cancelRole,cancelRole,transitionTag);
        String ticketFlow = """
            CREATE FUNCTION %s.b62_ticket_transition()
            RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
            SET search_path=pg_catalog AS %s
            BEGIN
              IF OLD.state='ISSUED' AND NEW.state='CONSUMED'
                 AND current_user='%s'
                 AND session_user IN ('%s','%s') THEN
                RETURN NEW;
              END IF;
              IF OLD.state='ISSUED' AND NEW.state='CANCELED'
                 AND current_user='%s' AND session_user='%s' THEN
                RETURN NEW;
              END IF;
              RAISE EXCEPTION 'B62_TICKET_TRANSITION_DENIED' USING ERRCODE='42501';
            END;
            %s;
            """.formatted(schema,transitionTag,guardRole,workerRole,otherWorkerRole,
                cancelRole,cancelRole,transitionTag);
        try (Connection c = owner(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute(ddl);
            s.execute(issuerPolicy);
            s.execute(approvalFlow);
            s.execute(ticketFlow);
            String approvalFn = schema + ".b62_approval_transition()";
            String ticketFn = schema + ".b62_ticket_transition()";
            s.execute("REVOKE ALL ON FUNCTION " + approvalFn + " FROM PUBLIC");
            s.execute("REVOKE ALL ON FUNCTION " + ticketFn + " FROM PUBLIC");
            s.execute("CREATE TRIGGER b62_approval_transition BEFORE UPDATE OF state "
                + "ON " + schema + ".b62_approved_effect_requests FOR EACH ROW "
                + "EXECUTE FUNCTION " + approvalFn);
            s.execute("CREATE TRIGGER b62_ticket_transition BEFORE UPDATE OF state "
                + "ON " + schema + ".b62_effect_tickets FOR EACH ROW "
                + "EXECUTE FUNCTION " + ticketFn);
            String triggerSignature = schema + ".b62_check_ticket_issuance()";
            s.execute("REVOKE ALL ON FUNCTION " + triggerSignature + " FROM PUBLIC");
            s.execute("ALTER FUNCTION " + triggerSignature + " OWNER TO " + guardRole);
            s.execute("CREATE TRIGGER b62_reject_unverified_ticket BEFORE INSERT "
                + "ON " + schema + ".b62_effect_tickets FOR EACH ROW "
                + "EXECUTE FUNCTION " + triggerSignature);
            String signature = schema + ".b62_record_ticket_effect(text,bigint,uuid,bigint,text,text,text,text,text,bigint,uuid,text)";
            s.execute("REVOKE ALL ON FUNCTION " + signature + " FROM PUBLIC");
            s.execute("ALTER FUNCTION " + signature + " OWNER TO " + guardRole);
            s.execute("GRANT EXECUTE ON FUNCTION " + signature + " TO " + workerRole + "," + otherWorkerRole);
            c.commit();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        alice = principal(42L, List.of("orchestrator:read", "orchestrator:write"));
        bob = principal(77L, List.of("orchestrator:read", "orchestrator:write"));
        workload = verifiedWorkload();
        lease = verifiedLease();
        assertTrue(lease.isBoundTo(workload));
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE b62_guarded_effects,b62_effect_tickets,b62_approved_effect_requests,b62_verified_scope_facts,synthetic_session_registration,project_memberships,tenant_memberships,aetheris_tasks,b62_local_resource_fence,signer_authorizations,principal_epochs");
            s.execute("INSERT INTO b62_local_resource_fence VALUES ('runtime','synthetic-primary',2,'ACTIVE',clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO aetheris_tasks(id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at) VALUES ('" + ALICE_TASK + "',42,'tenant-a','project-a',clock_timestamp()),('" + BOB_TASK + "',77,'tenant-b','project-b',clock_timestamp()),('" + OWNERLESS_TASK + "',NULL,NULL,NULL,NULL)");
            s.execute("INSERT INTO tenant_memberships VALUES ('tenant-a',42,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0),('tenant-b',77,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO project_memberships VALUES ('tenant-a','project-a',42,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0),('tenant-b','project-b',77,'ACTIVE',NULL,clock_timestamp()+interval '1 day',0)");
            s.execute("INSERT INTO signer_authorizations VALUES ('" + ISSUER + "','ticket-identity-key','ACTIVE',0)");
            s.execute("INSERT INTO principal_epochs VALUES (42,0,0),(77,0,0)");
            register(c, alice);
            register(c, bob);
        }
    }

    @Test
    void workerCannotInsertEffectsOrReadPrivateTickets() throws Exception {
        try (Connection c = worker()) {
            try (Statement s = c.createStatement()) {
                SQLException direct = assertThrows(SQLException.class,
                    () -> s.executeUpdate("INSERT INTO b62_guarded_effects VALUES ('"
                        + UUID.randomUUID() + "','" + OWNERLESS_TASK + "',2,'unsafe')"));
                assertEquals("42501", direct.getSQLState());
            }
            try (Statement s = c.createStatement()) {
                SQLException read = assertThrows(SQLException.class,
                    () -> s.executeQuery("SELECT ticket_id FROM b62_effect_tickets"));
                assertEquals("42501", read.getSQLState());
            }
            try (Statement s = c.createStatement()) {
                SQLException mint = assertThrows(SQLException.class,
                    () -> s.executeUpdate("INSERT INTO b62_effect_tickets VALUES ("
                        + "'00000000-0000-4000-8000-000000000000','x',"
                        + "'11111111-1111-4111-8111-111111111111',42,"
                        + "'tenant-a','project-a','i','k','j',1,'e',1,'w',"
                        + "'ISSUED',clock_timestamp())"));
                assertEquals("42501", mint.getSQLState());
            }
        }
        assertEquals(0L, countEffects());
    }

    @Test
    void authorizedTicketSingleUseAndCommittedDuplicateDenied() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            assertEquals(0L, countEffects(), "pending effect must not be visible");
            c.commit();
            assertEquals("CONSUMED", state(id));
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals(1L, countEffects());
    }

    @Test
    void noTicketForgedParametersAndUnregisteredTasksDeny() throws Exception {
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            c.rollback();
        }
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-b", "project-a", ALICE_TASK, id));
            assertFalse(effect(c, alice, "tenant-a", "project-a", OWNERLESS_TASK, id));
            assertFalse(effect(c, bob, "tenant-b", "project-b", BOB_TASK, id));
            assertEquals("ISSUED", state(id));
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals("ISSUED", state(id), "Rollback must restore ticket");
        assertEquals(0L, countEffects());
    }

    @Test
    void ticketIsSessionRoleBoundNotCallableByAnotherWorkerOrIssuer() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection c = workerB()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        try (Connection c = ticketIssuer(); Statement s = c.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                s.executeQuery("SELECT b62_record_ticket_effect("
                    + "'synthetic-primary',2,'" + ALICE_TASK + "',42,'tenant-a','project-a',"
                    + "'" + ISSUER + "','ticket-identity-key','x',1,'" + id + "','synthetic-worker')"));
            assertEquals("42501", ex.getSQLState(), "Issuer must not be effect caller");
        }
        assertEquals("ISSUED", state(id));
        assertEquals(0L, countEffects());
    }

    @Test
    void expiredTicketAndOwnerlessTaskDeny() throws Exception {
        UUID expired = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, expired, -2);
        UUID ownerless = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", OWNERLESS_TASK, ownerless, 40);
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, expired));
            assertFalse(effect(c, alice, "tenant-a", "project-a", OWNERLESS_TASK, ownerless));
            c.rollback();
        }
        assertEquals(0L, countEffects());
    }

    @Test
    void signerRevocationAndClosedFenceDenyEvenIssuedTickets() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("UPDATE signer_authorizations SET state='REVOKED'");
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("UPDATE signer_authorizations SET state='ACTIVE'");
            s.execute("UPDATE b62_local_resource_fence SET state='CLOSED'");
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals(0L, countEffects());
    }

    @Test
    void effectAndTicketRollbackAreAtomic() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals("ISSUED", state(id));
        assertEquals(0L, countEffects());
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertTrue(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.commit();
        }
        assertEquals("CONSUMED", state(id));
        assertEquals(1L, countEffects());
    }

    @Test
    void temporaryTableShadowCannotMintTicket() throws Exception {
        try (Connection c = worker(); Statement s = c.createStatement()) {
            s.execute("CREATE TEMP TABLE b62_effect_tickets(ticket_id uuid)");
            s.execute("INSERT INTO b62_effect_tickets VALUES ('" + UUID.randomUUID() + "')");
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, UUID.randomUUID()));
            c.rollback();
        }
        assertEquals(0L, countEffects());
    }


    @Test
    void concurrentTicketConsumptionSerializesUnderRowLock() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        try (Connection first = worker(); Connection contender = worker()) {
            first.setAutoCommit(false);
            assertTrue(effect(first, alice, "tenant-a", "project-a", ALICE_TASK, id));
            contender.setAutoCommit(false);
            try (Statement timeout = contender.createStatement()) {
                timeout.execute("SET LOCAL lock_timeout='350ms'");
            }
            SQLException blocked = assertThrows(SQLException.class, () ->
                effect(contender, alice, "tenant-a", "project-a", ALICE_TASK, id));
            assertEquals("55P03", blocked.getSQLState(),
                "Unconsumed ticket must be locked until first transaction settles");
            contender.rollback();
            first.commit();
            assertEquals("CONSUMED", state(id));
            assertFalse(effect(contender, alice, "tenant-a", "project-a", ALICE_TASK, id));
            contender.rollback();
        }
        assertEquals(1L, countEffects());
    }

    @Test
    void principalEpochRevocationRejectsPreviouslyIssuedTicket() throws Exception {
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        long cutoff = alice.requireRevocationFacts().signedIssuedAt().getEpochSecond();
        try (Connection c = owner(); PreparedStatement stmt = c.prepareStatement(
                "UPDATE principal_epochs SET reject_issued_at_or_before_epoch=? WHERE principal_id=42")) {
            stmt.setLong(1, cutoff);
            assertEquals(1, stmt.executeUpdate());
        }
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, alice, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals("ISSUED", state(id));
        assertEquals(0L, countEffects());
    }


    @Test
    void ticketExpiresWhileTransactionIsPendingYetEffectCommitsNoGo() throws Exception {
        // Negative evidence: checking clock_timestamp() on INSERT is not a
        // transaction-COMMIT deadline. Keep this counterexample as NO_GO.
        UUID id = UUID.randomUUID();
        approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 5);
        try (Connection pending = worker()) {
            pending.setAutoCommit(false);
            assertTrue(effect(pending, alice, "tenant-a", "project-a", ALICE_TASK, id));
            try (Statement delay = pending.createStatement()) {
                delay.execute("SELECT pg_sleep(6.25)");
            }
            try (Connection observer = owner(); PreparedStatement check =
                     observer.prepareStatement(
                         "SELECT expires_at < clock_timestamp() FROM b62_effect_tickets "
                             + "WHERE ticket_id=?")) {
                check.setObject(1, id);
                try (var row = check.executeQuery()) {
                    assertTrue(row.next());
                    assertTrue(row.getBoolean(1), "Ticket expiry must precede commit");
                }
            }
            pending.commit();
        }
        assertEquals("CONSUMED", state(id));
        assertEquals(1L, countEffects(),
            "NO_GO: transaction committed an effect after its ticket expired");
    }

    @Test
    void issuerCannotMintTicketForVerifiedReadOnlyPrincipal() throws Exception {
        var reader = principal(42L, List.of("orchestrator:read"));
        try (Connection admin = owner()) {
            register(admin, reader);
        }
        UUID id = UUID.randomUUID();
        SQLException rejected = assertThrows(SQLException.class, () ->
            approvedIssue(workerRole, reader, "tenant-a", "project-a", ALICE_TASK, id, 40));
        assertEquals("42501", rejected.getSQLState(),
            "Even an INSERT-only ticket issuer must not grant Java-only write scope");
        try (Connection c = worker()) {
            c.setAutoCommit(false);
            assertFalse(effect(c, reader, "tenant-a", "project-a", ALICE_TASK, id));
            c.rollback();
        }
        assertEquals(0L, countEffects());
    }

    @Test
    void issuerCannotRedirectTicketToOtherWorkerRole() throws Exception {
        SQLException rejected = assertThrows(SQLException.class, () ->
            approvedIssue(otherWorkerRole, alice, "tenant-a", "project-a", ALICE_TASK,
                UUID.randomUUID(), 40));
        assertEquals("42501", rejected.getSQLState());
        assertEquals(0L, countEffects());
    }

    @Test
    void issuerCannotForgeOrAlterVerifiedPrincipalScopeFacts() throws Exception {
        try (Connection c = ticketIssuer(); Statement sql = c.createStatement()) {
            SQLException insert = assertThrows(SQLException.class, () ->
                sql.executeUpdate("INSERT INTO b62_verified_scope_facts VALUES "
                    + "('forged','kid','jti',999,'orchestrator:write')"));
            assertEquals("42501", insert.getSQLState());
            SQLException modify = assertThrows(SQLException.class, () ->
                sql.executeUpdate("DELETE FROM b62_verified_scope_facts"));
            assertEquals("42501", modify.getSQLState());
        }
        assertEquals(0L, countEffects());
    }

    @Test
    void issuerCannotMintAfterSessionOrPrincipalRevocation() throws Exception {
        var facts = alice.requireRevocationFacts();
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            sql.execute("UPDATE principal_epochs SET reject_issued_at_or_before_epoch="
                + facts.signedIssuedAt().getEpochSecond() + " WHERE principal_id=42");
        }
        SQLException epochDenied = assertThrows(SQLException.class, () ->
            approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK,
                UUID.randomUUID(), 40));
        assertEquals("42501", epochDenied.getSQLState());
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            sql.execute("UPDATE principal_epochs SET reject_issued_at_or_before_epoch=0 WHERE principal_id=42");
            sql.execute("UPDATE synthetic_session_registration SET state='REVOKED' "
                + "WHERE jti='" + facts.signedTokenId() + "'");
        }
        SQLException sessionDenied = assertThrows(SQLException.class, () ->
            approvedIssue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK,
                UUID.randomUUID(), 40));
        assertEquals("42501", sessionDenied.getSQLState());
        assertEquals(0L, countEffects());
    }


    @Test
    void issuerCannotMintWithoutAnIndependentlyApprovedExactRequest() throws Exception {
        UUID id = UUID.randomUUID();
        SQLException denied = assertThrows(SQLException.class, () ->
            issue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40));
        assertEquals("42501", denied.getSQLState());
        assertEquals(0L, countEffects());
    }

    @Test
    void issuerCannotReadWriteOrDeleteIndependentRequestApprovals() throws Exception {
        try (Connection c = ticketIssuer(); Statement sql = c.createStatement()) {
            SQLException read = assertThrows(SQLException.class, () ->
                sql.executeQuery("SELECT effect_id FROM b62_approved_effect_requests"));
            assertEquals("42501", read.getSQLState());
            SQLException insert = assertThrows(SQLException.class, () ->
                sql.executeUpdate("INSERT INTO b62_approved_effect_requests VALUES "
                    + "('00000000-0000-4000-8000-000000000000','forged','forged',"
                    + "'" + ALICE_TASK + "',42,'tenant-a','project-a','i','k','j',"
                    + "1,'synthetic-primary',2,'synthetic-worker','APPROVED',clock_timestamp())"));
            assertEquals("42501", insert.getSQLState());
            SQLException erase = assertThrows(SQLException.class, () ->
                sql.executeUpdate("DELETE FROM b62_approved_effect_requests"));
            assertEquals("42501", erase.getSQLState());
        }
    }

    @Test
    void issuerCannotRetargetApprovedEffectToDifferentProjectOrTask() throws Exception {
        UUID id = UUID.randomUUID();
        approveRequest(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 55);
        SQLException wrongProject = assertThrows(SQLException.class, () ->
            issue(workerRole, alice, "tenant-a", "project-b", ALICE_TASK, id, 40));
        assertEquals("42501", wrongProject.getSQLState());
        SQLException wrongTask = assertThrows(SQLException.class, () ->
            issue(workerRole, alice, "tenant-a", "project-a", BOB_TASK, id, 40));
        assertEquals("42501", wrongTask.getSQLState());
        issue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40);
        assertEquals("ISSUED", state(id));
        assertEquals(0L, countEffects());
    }

    @Test
    void ticketIssuerCannotReuseApprovalOrExtendItsExpiration() throws Exception {
        UUID id = UUID.randomUUID();
        approveRequest(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 12);
        SQLException tooLong = assertThrows(SQLException.class, () ->
            issue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 40));
        assertEquals("42501", tooLong.getSQLState());
        issue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 5);
        // Simulate a privileged test-only ticket loss. Approval must still
        // prevent a second issuance with the same effect identifier.
        try (Connection admin = owner(); Statement sql = admin.createStatement()) {
            sql.executeUpdate("DELETE FROM b62_effect_tickets WHERE ticket_id='" + id + "'");
        }
        SQLException reuse = assertThrows(SQLException.class, () ->
            issue(workerRole, alice, "tenant-a", "project-a", ALICE_TASK, id, 5));
        assertEquals("42501", reuse.getSQLState());
        assertEquals(0L, countEffects());
    }


    @Test
    void cancellationBeforeIssuanceDeniesAndCannotBeReopened() throws Exception {
        UUID id=UUID.randomUUID();
        approveRequest(workerRole,alice,"tenant-a","project-a",ALICE_TASK,id,55);
        assertEquals(1,cancelApproval(id));
        assertEquals("CANCELED",approvalState(id));
        SQLException blocked=assertThrows(SQLException.class,()->
            issue(workerRole,alice,"tenant-a","project-a",ALICE_TASK,id,40));
        assertEquals("42501",blocked.getSQLState());
        try (Connection c=canceller(); PreparedStatement p=c.prepareStatement(
                "UPDATE b62_approved_effect_requests SET state='APPROVED' WHERE effect_id=?")) {
            p.setObject(1,id);
            SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
            assertEquals("42501",denied.getSQLState());
        }
        assertEquals("CANCELED",approvalState(id));
        assertEquals(0L,countEffects());
    }

    @Test
    void cancellationOfUnusedTicketDeniesAndCannotBeReopened() throws Exception {
        UUID id=UUID.randomUUID();
        approvedIssue(workerRole,alice,"tenant-a","project-a",ALICE_TASK,id,40);
        assertEquals(1,cancelTicket(id));
        assertEquals("CANCELED",state(id));
        try(Connection c=worker()){
            c.setAutoCommit(false);
            assertFalse(effect(c,alice,"tenant-a","project-a",ALICE_TASK,id));
            c.rollback();
        }
        try(Connection c=canceller(); PreparedStatement p=c.prepareStatement(
                "UPDATE b62_effect_tickets SET state='ISSUED' WHERE ticket_id=?")){
            p.setObject(1,id);
            SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
            assertEquals("42501",denied.getSQLState());
        }
        assertEquals("CANCELED",state(id));
        assertEquals(0L,countEffects());
    }

    @Test
    void cancellerCannotForgePositiveAuthorityTransitions() throws Exception {
        UUID pending=UUID.randomUUID();
        approveRequest(workerRole,alice,"tenant-a","project-a",ALICE_TASK,pending,55);
        try(Connection c=canceller();PreparedStatement p=c.prepareStatement(
                "UPDATE b62_approved_effect_requests SET state='ISSUED' WHERE effect_id=?")){
            p.setObject(1,pending);
            SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
            assertEquals("42501",denied.getSQLState());
        }
        assertEquals("APPROVED",approvalState(pending));
        UUID issued=UUID.randomUUID();
        approvedIssue(workerRole,alice,"tenant-a","project-a",ALICE_TASK,issued,40);
        try(Connection c=canceller();PreparedStatement p=c.prepareStatement(
                "UPDATE b62_effect_tickets SET state='CONSUMED' WHERE ticket_id=?")){
            p.setObject(1,issued);
            SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
            assertEquals("42501",denied.getSQLState());
        }
        assertEquals("ISSUED",state(issued));
        assertEquals(0L,countEffects());
    }

    @Test
    void issuerAndWorkerCannotCancelTicket() throws Exception {
        UUID id=UUID.randomUUID();
        approvedIssue(workerRole,alice,"tenant-a","project-a",ALICE_TASK,id,40);
        try(Connection issuer=ticketIssuer(); Connection workerSession=worker()){
            for(Connection c:List.of(issuer,workerSession)){
                try(PreparedStatement p=c.prepareStatement(
                        "UPDATE b62_effect_tickets SET state='CANCELED' WHERE ticket_id=?")){
                    p.setObject(1,id);
                    SQLException denied=assertThrows(SQLException.class,p::executeUpdate);
                    assertEquals("42501",denied.getSQLState());
                }
            }
        }
        assertEquals("ISSUED",state(id));
    }

    @Test
    void cancellationCannotOvertakeAnAlreadyPendingEffect() throws Exception {
        UUID id=UUID.randomUUID();
        approvedIssue(workerRole,alice,"tenant-a","project-a",ALICE_TASK,id,40);
        try(Connection pending=worker(); Connection cancellation=canceller()){
            pending.setAutoCommit(false);
            assertTrue(effect(pending,alice,"tenant-a","project-a",ALICE_TASK,id));
            cancellation.setAutoCommit(false);
            try(Statement s=cancellation.createStatement()){
                s.execute("SET LOCAL lock_timeout='350ms'");
            }
            try(PreparedStatement p=cancellation.prepareStatement(
                    "UPDATE b62_effect_tickets SET state='CANCELED' "
                        + "WHERE ticket_id=? AND state='ISSUED'")){
                p.setObject(1,id);
                SQLException blocked=assertThrows(SQLException.class,p::executeUpdate);
                assertEquals("55P03",blocked.getSQLState());
            }
            cancellation.rollback();
            pending.commit();
        }
        assertEquals("CONSUMED",state(id));
        assertEquals(0,cancelTicket(id));
        assertEquals(1L,countEffects());
    }

    @AfterAll
    static void eraseDisposableObjects() throws Exception {
        if (schema == null) return;
        assertTrue(schema.matches("b62_ticket_[0-9a-f]{32}"));
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            for (String role : List.of(workerRole, otherWorkerRole, issuerRole, guardRole, cancelRole)) {
                s.execute("DROP ROLE IF EXISTS " + role);
            }
            try (PreparedStatement find = c.prepareStatement(
                    "SELECT COUNT(*) FROM pg_namespace WHERE nspname=?")) {
                find.setString(1, schema);
                try (var rs = find.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0L, rs.getLong(1));
                }
            }
        }
    }
}
