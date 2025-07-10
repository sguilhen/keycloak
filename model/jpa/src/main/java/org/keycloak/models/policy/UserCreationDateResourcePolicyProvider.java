package org.keycloak.models.policy;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.entities.UserEntity;

public class UserCreationDateResourcePolicyProvider implements ResourcePolicyProvider {

    private final ComponentModel model;
    private KeycloakSession session;

    public UserCreationDateResourcePolicyProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public List<String> getResources(Long time) {
        RealmModel realm = session.getContext().getRealm();
        EntityManager entityManager = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<UserEntity> from = query.from(UserEntity.class);

        // add the predicates here

        return session.users().searchForUserStream(realm, Map.of(UserModel.CREATED_AFTER_TIMESTAMP, String.valueOf(time)))
                .map(UserModel::getId).toList();
    }
}
