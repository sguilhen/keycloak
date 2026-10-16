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

package org.keycloak.models.cache;

import org.keycloak.connections.jpa.support.EntityManagers;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.cache.authorization.CachedStoreFactoryProvider;

public class CacheManagedModelsUtil {

    private CacheManagedModelsUtil() {
    }

    /**
     * Flushes and clears the persistence context, then clears the request-scoped
     * "managed model" maps of the realm, user, and authorization cache providers
     * (if present), so they stop returning stale, now-detached adapters.
     * See {@link EntityManagers#flush}.
     */
    public static void flushAndClearManagedModels(KeycloakSession session) {
        EntityManagers.flush(session, true);

        CacheRealmProvider realmCache = session.getProvider(CacheRealmProvider.class);
        if (realmCache != null) {
            realmCache.clearManagedModels();
        }

        UserCache userCache = session.getProvider(UserCache.class);
        if (userCache != null) {
            userCache.clearManagedModels();
        }

        CachedStoreFactoryProvider storeCache = session.getProvider(CachedStoreFactoryProvider.class);
        if (storeCache != null) {
            storeCache.clearManagedModels();
        }
    }
}
