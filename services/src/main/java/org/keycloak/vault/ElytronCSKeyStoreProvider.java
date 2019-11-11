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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * A {@link VaultProvider} implementation that uses the Elytron keystore-based credential store implementation to retrieve secrets.
 * Elytron credential stores can be created and managed using either the elytron subsystem in WildFly/EAP or the elytron tool.
 * <p/>
 * This provider requires that entries in the credential store (keystore) follow the same {@code realm_key} pattern required by
 * the {@link FilesPlainTextVaultProvider}. If the {@code realm} or {@code key} contains an underscore, it must be escaped by
 * another underscore character. For example:
 * <pre>
 *     realm name: master, key=mysecret - expected credential store key: master_mysecret
 *     realm name: my_realm, key=my_secret - expected credential store key: my__realm_my__secret
 * </pre>
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class ElytronCSKeyStoreProvider implements VaultProvider {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private final String realmName;
    private final Map<String, String> storeAttributes;
    private final CredentialStore.CredentialSourceProtectionParameter storeProtectionParam;

    private CredentialStore credentialStore;
    private long lastModified;

    public ElytronCSKeyStoreProvider(final String realmName, final Map<String, String> attributes,
                                     final CredentialStore.CredentialSourceProtectionParameter protectionParam) {
        this.realmName = realmName;
        this.storeAttributes = attributes;
        this.storeProtectionParam = protectionParam;
        // store the last modified time of the keystore.
        this.lastModified = this.getKeyStoreLastModifiedTime();
        // build and initialize the credential store.
        this.credentialStore = buildCredentialStore();
    }

    @Override
    public VaultRawSecret obtainSecret(String vaultSecretId) {
        // check if we need to reload the credential store.
        long lastModifiedTime = this.getKeyStoreLastModifiedTime();
        if (this.lastModified != lastModifiedTime) {
            this.lastModified = lastModifiedTime;
            this.credentialStore = buildCredentialStore();
        }
        if (this.credentialStore == null) {
            return DefaultVaultRawSecret.forBuffer(Optional.empty());
        }

        String storeAlias = this.realmName.replaceAll("_", "__") + "_" + vaultSecretId.replaceAll("_", "__");
        try {
            PasswordCredential credential = this.credentialStore.retrieve(storeAlias, PasswordCredential.class);
            if (credential == null) {
                // alias not found, password type doesn't match entry, or algorithm (clear) doesn't match entry.
                logger.warnf("Cannot find secret %s in credential store", vaultSecretId);
                return DefaultVaultRawSecret.forBuffer(Optional.empty());
            }
            char[] secret = credential.getPassword().castAndApply(ClearPassword.class, ClearPassword::getPassword);
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
            return DefaultVaultRawSecret.forBuffer(Optional.of(buffer));
        } catch (CredentialStoreException e) {
            // this might happen if there is an error when trying to retrieve the secret from the store.
            logger.warnf(e,"Unable to retrieve secret %s from credential store", vaultSecretId);
            return DefaultVaultRawSecret.forBuffer(Optional.empty());
        }
    }

    @Override
    public void close() {
    }

    /**
     * Obtains the last modified time (in millis) of the underlying key store.
     *
     * @return the last modified time.
     */
    private long getKeyStoreLastModifiedTime() {
        long lastModifiedTime = 0;
        try {
            FileTime fileTime = (FileTime) Files.getAttribute(Paths.get(this.storeAttributes.get("location")), "lastModifiedTime");
            lastModifiedTime = fileTime.toMillis();
        } catch(IOException e) {
            logger.debug("Unable to retrieve keystore last modified time");
        }
        return lastModifiedTime;
    }

    /**
     * Builds and initializes the {@code CredentialStore}. If an error occurs this method logs a WARN message with the error and
     * returns {@code null}.
     *
     * @return the initialized {@code CredentialStore}.
     */
    private CredentialStore buildCredentialStore() {
        CredentialStore store = null;
        try {
            store = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE);
            store.initialize(this.storeAttributes, this.storeProtectionParam);
        } catch(NoSuchAlgorithmException | CredentialStoreException e) {
            logger.warn("Error instantiating credential store", e);
        }
        return store;
    }
}
