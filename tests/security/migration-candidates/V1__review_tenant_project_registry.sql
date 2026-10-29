-- REVIEW-ONLY S0-C versioned migration CANDIDATE; not on Spring/Flyway classpath.
-- Apply ONLY to isolated disposable PostgreSQL16 databases with explicit owner
-- authorization. Real governance, role separation, rollout and backups pending.
-- Identity UID is the signed Identity account ID, NOT UserService.users.id.
CREATE TABLE registry_tenants (
 tenant_key varchar(96) PRIMARY KEY
   CHECK (tenant_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TABLE registry_projects (
 tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
 project_key varchar(96) NOT NULL
   CHECK (project_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
 PRIMARY KEY(tenant_key,project_key)
);
CREATE TABLE registry_tenant_memberships (
 tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
 identity_uid bigint NOT NULL CHECK (identity_uid > 0),
 member_role varchar(16) NOT NULL
   CHECK (member_role IN ('OWNER','ADMIN','MEMBER','VIEWER')),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
 revoked_at timestamptz,
 valid_until timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
 PRIMARY KEY(tenant_key,identity_uid),
 CONSTRAINT s0_tenant_membership_revoke_consistency CHECK (
  (status='ACTIVE' AND revoked_at IS NULL) OR
  (status='REVOKED' AND revoked_at IS NOT NULL)
 )
);
CREATE TABLE registry_project_memberships (
 tenant_key varchar(96) NOT NULL,
 project_key varchar(96) NOT NULL,
 identity_uid bigint NOT NULL CHECK (identity_uid > 0),
 member_role varchar(16) NOT NULL
   CHECK (member_role IN ('ADMIN','MEMBER','VIEWER')),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
 revoked_at timestamptz,
 valid_until timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
 PRIMARY KEY(tenant_key,project_key,identity_uid),
 CONSTRAINT s0_project_parent_fk FOREIGN KEY(tenant_key,project_key)
   REFERENCES registry_projects(tenant_key,project_key),
 CONSTRAINT s0_project_member_parent_fk FOREIGN KEY(tenant_key,identity_uid)
   REFERENCES registry_tenant_memberships(tenant_key,identity_uid),
 CONSTRAINT s0_project_membership_revoke_consistency CHECK (
  (status='ACTIVE' AND revoked_at IS NULL) OR
  (status='REVOKED' AND revoked_at IS NOT NULL)
 )
);
