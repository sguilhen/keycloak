/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.testsuite.organization.exportimport;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.exportimport.ExportImportConfig;
import org.keycloak.exportimport.dir.DirExportProviderFactory;
import org.keycloak.exportimport.dir.DirImportProviderFactory;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.organization.admin.AbstractOrganizationTest;
import org.keycloak.testsuite.util.LDAPRule;
import org.keycloak.testsuite.util.runonserver.ExportImportHelper;
import org.keycloak.testsuite.util.runonserver.LdapHelper;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for https://github.com/keycloak/keycloak/issues/52594: a directory-based export/import that spans
 * multiple user batches must not leave stale, detached user-cache adapters behind for a federated (LDAP) organization
 * member, which would otherwise surface as a {@code LazyInitializationException}.
 */
public class OrganizationExportWithLdapTest extends AbstractOrganizationTest {

    @ClassRule
    public static LDAPRule ldapRule = new LDAPRule();

    @Override
    public void importTestRealms() {
        super.importTestRealms();

        Map<String, String> cfg = ldapRule.getConfig();
        runOnServer.fetchString(LdapHelper.createLDAPProvider(cfg, true));
        runOnServer.run(LdapHelper.prepareGroupsLDAPTest());
    }

    @After
    public void afterExportImport() {
        runOnServerMaster.run(ExportImportHelper.clear());
    }

    @Test
    public void testExportImportWithLdapOrgMemberAcrossBatches() {
        OrganizationResource organization = managedRealm.admin().organizations().get(createOrganization().getId());

        // a plain (non-federated) member, expected to land in the first export batch
        addMember(organization, "localuser@neworg.org");

        // a federated LDAP member: with usersPerFile=1 this lands in a later batch, after the first batch's
        // flush + persistence-context clear has already run
        UserRepresentation ldapUser = managedRealm.admin().users().searchByUsername("johnkeycloak", true).get(0);
        try (Response response = organization.members().addMember(ldapUser.getId())) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        runOnServerMaster.run(ExportImportHelper.setUsersPerFile(1));

        RealmRepresentation imported = exportRemoveImportDirRealm();

        assertTrue(imported.isOrganizationsEnabled());

        List<OrganizationRepresentation> organizations = managedRealm.admin().organizations().list(-1, -1);
        assertThat(organizations, hasSize(1));

        List<MemberRepresentation> members = managedRealm.admin().organizations().get(organizations.get(0).getId())
                .members().list(-1, -1);
        assertThat(members, hasSize(2));
    }

    private RealmRepresentation exportRemoveImportDirRealm() {
        runOnServerMaster.run(ExportImportHelper.setProvider(DirExportProviderFactory.PROVIDER_ID));
        String dir = runOnServerMaster.fetchString(ExportImportHelper.getExportImportTestDirectory()).replace("\"", "");
        runOnServerMaster.run(ExportImportHelper.setDir(dir));
        runOnServerMaster.run(ExportImportHelper.setAction(ExportImportConfig.ACTION_EXPORT));
        runOnServerMaster.run(ExportImportHelper.setRealmName(managedRealm.admin().toRepresentation().getRealm()));
        runOnServerMaster.run(ExportImportHelper.runExport());

        managedRealm.admin().remove();

        runOnServerMaster.run(ExportImportHelper.setProvider(DirImportProviderFactory.PROVIDER_ID));
        runOnServerMaster.run(ExportImportHelper.setDir(dir));
        runOnServerMaster.run(ExportImportHelper.setAction(ExportImportConfig.ACTION_IMPORT));
        runOnServerMaster.run(ExportImportHelper.runImport());
        getCleanup().addCleanup(() -> {
            managedRealm.admin().remove();
            getTestContext().getTestRealmReps().clear();
        });

        return managedRealm.admin().toRepresentation();
    }
}
