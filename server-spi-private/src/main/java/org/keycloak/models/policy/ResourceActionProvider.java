package org.keycloak.models.policy;

import java.util.List;
import java.util.function.Function;

import org.keycloak.provider.Provider;

public interface ResourceActionProvider extends Provider {

    void run(Function<Long, List<String>> resources);

    boolean isRunnable();
}
