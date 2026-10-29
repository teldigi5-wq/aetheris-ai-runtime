-- REVIEW-ONLY S0-C schema-change CANDIDATE. NOT deployed / NOT on Flyway path.
-- Requires prior V1 and a canonical existing JPA-shaped aetheris_tasks table.
-- Zero automatic backfill, no default owner, no route activation. Preexisting
-- tasks remain NULL/ownerless/quarantined. Constraints disallow partial bindings.
ALTER TABLE aetheris_tasks
 ADD COLUMN owner_identity_uid bigint,
 ADD COLUMN owner_tenant_key varchar(96),
 ADD COLUMN owner_project_key varchar(96),
 ADD COLUMN owner_verified_at timestamptz;

ALTER TABLE aetheris_tasks
 ADD CONSTRAINT s0_task_owner_all_or_none CHECK (
  (owner_identity_uid IS NULL AND owner_tenant_key IS NULL
   AND owner_project_key IS NULL AND owner_verified_at IS NULL)
  OR
  (owner_identity_uid > 0 AND owner_tenant_key IS NOT NULL
   AND owner_tenant_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'
   AND owner_project_key IS NOT NULL
   AND owner_project_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'
   AND owner_verified_at IS NOT NULL)
 ) NOT VALID;
ALTER TABLE aetheris_tasks VALIDATE CONSTRAINT s0_task_owner_all_or_none;

-- Composite FK does not itself prove current ACTIVE membership. Revocation
-- must remain a status transition, not a destructive delete of linked records.
ALTER TABLE aetheris_tasks
 ADD CONSTRAINT s0_task_owner_project_member_fk
 FOREIGN KEY(owner_tenant_key,owner_project_key,owner_identity_uid)
 REFERENCES registry_project_memberships(tenant_key,project_key,identity_uid)
 ON DELETE RESTRICT NOT VALID;
ALTER TABLE aetheris_tasks VALIDATE CONSTRAINT s0_task_owner_project_member_fk;

-- Potential large-table blocking DDL. No automatic production use; assess
-- CONCURRENTLY indexing / lock_timeout / maintenance budget separately.
CREATE INDEX idx_s0_tasks_scoped_owner
 ON aetheris_tasks(owner_tenant_key,owner_project_key,owner_identity_uid,updated_at DESC)
 WHERE owner_identity_uid IS NOT NULL;

-- Prevent implicit reassignment and claiming old tasks through ordinary UPDATE.
-- Separately approved, audited owner-transfer policy must be designed before
-- granting any future transfer. Does not replace verified INSERT authorization.
CREATE FUNCTION s0_deny_task_owner_binding_update()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog AS $owner_lock$
BEGIN
 IF ROW(NEW.owner_identity_uid,NEW.owner_tenant_key,NEW.owner_project_key,NEW.owner_verified_at)
    IS DISTINCT FROM
    ROW(OLD.owner_identity_uid,OLD.owner_tenant_key,OLD.owner_project_key,OLD.owner_verified_at)
 THEN
   RAISE EXCEPTION 'immutable task owner binding' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END
$owner_lock$;
CREATE TRIGGER s0_lock_task_owner_binding
 BEFORE UPDATE ON aetheris_tasks
 FOR EACH ROW EXECUTE FUNCTION s0_deny_task_owner_binding_update();
