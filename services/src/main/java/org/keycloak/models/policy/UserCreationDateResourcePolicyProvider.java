package org.keycloak.models.policy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

public class UserCreationDateResourcePolicyProvider implements ResourcePolicyProvider {

    private final ComponentModel model;
    private KeycloakSession session;

    public UserCreationDateResourcePolicyProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    public static Policy builder() {
        return new Policy(UserCreationDateResourcePolicyProviderFactory.ID);
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public List<String> getResources(Long time) {
        return session.users().searchForUserStream(session.getContext().getRealm(), Map.of())
                .map(UserModel::getId).toList();
    }

    public static class Policy extends ResourcePolicy {

        public Policy(String providerId) {
            super(providerId);
        }

        public Policy createdAfter(Duration duration) {
            setConfig("createdAfter", String.valueOf(duration.toMillis()));
            return this;
        }
    }
}
