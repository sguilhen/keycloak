package org.keycloak.models.policy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class DisableUserActionProvider implements ResourceActionProvider {

    private final KeycloakSession session;
    private final ComponentModel model;

    public static Action builder() {
        return new Action(DisableUserActionProviderFactory.ID);
    }

    public DisableUserActionProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public void close() {

    }

    @Override
    public void run(Function<Long, List<String>> resources) {
        Action action = new Action(model);
        Long createdAfter = action.getCreatedAfter();

        if (createdAfter  <= 0) {
            return;
        }

        RealmModel realm = session.getContext().getRealm();

        for (String id : resources.apply(createdAfter)) {
            UserModel user = session.users().getUserById(realm, id);
            user.setEnabled(false);
        }
    }

    @Override
    public boolean isRunnable() {
        return model.get("time") == null;
    }

    public static class Action extends ResourceAction {

        public Action(String providerId) {
            super(providerId);
        }

        private Action(ComponentModel model) {
            super(model);
        }

        public Action createdAfter(Duration duration) {
            setConfig("createdAfter", String.valueOf(duration.toMillis()));
            return this;
        }

        private Long getCreatedAfter() {
            return Long.valueOf(getConfig().getFirstOrDefault("createdAfter", "0"));
        }
    }
}
