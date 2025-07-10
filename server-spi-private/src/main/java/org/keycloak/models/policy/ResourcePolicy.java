package org.keycloak.models.policy;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;

public class ResourcePolicy {

    private String providerId;
    private String id;
    private MultivaluedHashMap<String, String> config;

    public ResourcePolicy() {
        // reflection
    }

    public ResourcePolicy(String providerId) {
        this.providerId = providerId;
        this.id = null;
    }

    public ResourcePolicy(ComponentModel c) {
        this.id = c.getId();
        this.providerId = c.getProviderId();
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public ResourcePolicy setConfig(String key, String value) {
        if (config == null) {
            config = new MultivaluedHashMap<>();
        }
        this.config.putSingle(key, value);
        return this;
    }

    public MultivaluedHashMap<String, String> getConfig() {
        return config;
    }
}
