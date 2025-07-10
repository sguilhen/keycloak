package org.keycloak.models.policy;

import java.util.List;
import java.util.function.Function;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;

public class DisableUserActionProvider implements ResourceActionProvider {

    final KeycloakSession session;
    private final ComponentModel model;

    public DisableUserActionProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public void close() {

    }

    @Override
    public void run(Function<Long, List<String>> resources) {
    }

    @Override
    public boolean isRunnable() {
        return model.get("time") == null;
    }
}
