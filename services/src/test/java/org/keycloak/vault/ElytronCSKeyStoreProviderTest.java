/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.vault;

import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.keycloak.vault.SecretContains.secretContains;

/**
 * Tests for the {@link ElytronCSKeyStoreProvider} and associated {@link ElytronCSKeyStoreProviderFactory}.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class ElytronCSKeyStoreProviderTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * Tests the creation of a provider using the {@link ElytronCSKeyStoreProviderFactory}. The test plays with the configuration
     * to check if the factory returns a proper {@link ElytronCSKeyStoreProvider} instance when fed with valid configuration and
     * if a {@code null} provider is returned if a required configuration property is missing. It also checks if an exception
     * is raised if the configuration points to a keystore file that doesn't exist.
     *
     * @throws Exception if an error occurs while running the test.
     */
    @Test
    public void testCreateProvider() throws Exception {
        // init the factory with valid config and check it can successfully create a provider instance.
        ProviderConfig config = new ProviderConfig("src/test/resources/org/keycloak/vault/credential-store.jceks", "secretpw1!");
        ElytronCSKeyStoreProviderFactory factory = new ElytronCSKeyStoreProviderFactory() {
            @Override
            protected String getRealmName(KeycloakSession session) {
                return "master";
            }
        };
        factory.init(config);
        VaultProvider provider = factory.create(null);
        assertNotNull(provider);

        // init the factory without a location and check that it returns null provider on create.
        config.setLocation(null);
        factory.init(config);
        provider = factory.create(null);
        assertNull(provider);

        // init the factory without a password and check that it returns null provider on create.
        config.setLocation("src/test/resources/org/keycloak/vault/credential-store.jceks");
        config.setPassword(null);
        factory.init(config);
        provider = factory.create(null);
        assertNull(provider);

        // try to init the factory with an invalid keystore location and check that an exception is raised.
        config.setLocation("src/test/resources/org/keycloak/vault/non-existing.jceks");
        this.expectedException.expect(VaultNotFoundException.class);
        factory.init(config);
    }

    /**
     * Tests the retrieval of secrets using the {@link ElytronCSKeyStoreProvider}. The test relies on the factory to obtain
     * an instance of the provider and then checks if the provider is capable of retrieving secrets using the configured
     * elytron credential store.
     *
     * @throws Exception if an error occurs while running the test.
     */
    @Test
    public void testRetrieveSecretFromVault() throws Exception {
        ProviderConfig config = new ProviderConfig("src/test/resources/org/keycloak/vault/credential-store.jceks",
                "MASK-3u2HNQaMogJJ8VP7J6gRIl;12345678;321");
        ElytronCSKeyStoreProviderFactory factory = new ElytronCSKeyStoreProviderFactory() {
            @Override
            protected String getRealmName(KeycloakSession session) {
                return "master";
            }
        };

        factory.init(config);
        VaultProvider provider = factory.create(null);
        assertNotNull(provider);

        // obtain a secret using a key that exists (key has underscore, so it is transformed into master_smtp__key).
        VaultRawSecret secret = provider.obtainSecret("smtp_key");
        assertNotNull(secret);
        assertTrue(secret.get().isPresent());
        assertThat(secret, secretContains("secure_master_smtp_secret"));

        // try to retrieve a secret using a key that doesn't exist.
        secret = provider.obtainSecret("another_key");
        assertNotNull(secret);
        assertFalse(secret.get().isPresent());
    }

    /**
     * Implementation of {@link Config.Scope} to be used for the tests.
     */
    private static class ProviderConfig implements Config.Scope {

        private Map<String, String> config = new HashMap<>();

        public ProviderConfig(final String location, final String password) {
            this.config.put("location", location);
            this.config.put("secret", password);
        }

        public void setLocation(final String location) {
            this.config.put("location", location);
        }

        public void setPassword(final String password) {
            this.config.put("secret", password);
        }

        @Override
        public String get(String key) {
            return this.config.get(key);
        }

        @Override
        public String get(String key, String defaultValue) {
            return this.config.get(key) != null ? this.config.get(key) : defaultValue;
        }

        @Override
        public String[] getArray(String key) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Integer getInt(String key) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Integer getInt(String key, Integer defaultValue) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Long getLong(String key) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Long getLong(String key, Long defaultValue) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Boolean getBoolean(String key) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Boolean getBoolean(String key, Boolean defaultValue) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Config.Scope scope(String... scope) {
            throw new UnsupportedOperationException("not implemented");
        }
    }

}
