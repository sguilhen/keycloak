package org.keycloak.models.policy;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;

public class ResourceAction {

    private String id;
    private String providerId;
    private MultivaluedHashMap<String, String> config;

    public ResourceAction() {
        // reflection
    }

    public ResourceAction(String providerId) {
        this.providerId = providerId;
    }

    public ResourceAction(ComponentModel model) {
        this.id = model.getId();
        this.providerId = model.getProviderId();
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public ResourceAction setConfig(String key, String value) {
        if (config == null) {
            config = new MultivaluedHashMap<>();
        }
        this.config.putSingle(key, value);
        return this;
    }

    public MultivaluedHashMap<String, String> getConfig() {
        if (config == null) {
            return new MultivaluedHashMap<>();
        }
        return config;
    }
}
