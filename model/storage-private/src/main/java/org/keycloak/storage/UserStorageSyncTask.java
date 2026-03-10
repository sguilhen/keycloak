package org.keycloak.storage;

import org.keycloak.cluster.ClusterProvider;
import org.keycloak.cluster.ExecutionResult;
import org.keycloak.common.util.Time;
import org.keycloak.common.util.TriFunction;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ModelIllegalStateException;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProviderModel.SyncMode;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.timer.ScheduledTask;
import org.keycloak.timer.TimerProvider;
import org.keycloak.timer.TimerProvider.TimerTaskContext;

import org.jboss.logging.Logger;

final class UserStorageSyncTask implements ScheduledTask {

    private static final Logger logger = Logger.getLogger(UserStorageSyncTask.class);
    private static final int TASK_EXECUTION_TIMEOUT = 30;

    private final String providerId;
    private final String realmId;
    private final SyncMode syncMode;
    private final int period;

    UserStorageSyncTask(UserStorageProviderModel provider, SyncMode syncMode) {
        this.providerId = provider.getId();
        this.realmId = provider.getParentId();
        this.syncMode = syncMode;
        this.period = SyncMode.FULL.equals(syncMode) ? provider.getFullSyncPeriod() : provider.getChangedSyncPeriod();
    }

    @Override
    public void run(KeycloakSession session) {
        RealmModel realm = session.realms().getRealm(realmId);

        session.getContext().setRealm(realm);

        UserStorageProviderModel provider = getStorageModel(session);

        if (isSyncPeriod(provider)) {
            runWithResult(session);
            return;
        }

        logger.debugf("Ignored LDAP %s users-sync with storage provider %s due small time since last sync in realm %s", //
                syncMode, provider.getName(), realmId);
    }

    @Override
    public String getTaskName() {
        return UserStorageSyncTask.class.getSimpleName() + "-" + providerId + "-" + syncMode;
    }

    SynchronizationResult runWithResult(KeycloakSession session) {
        try {
            return switch (syncMode) {
                case FULL -> runFullSync(session);
                case CHANGED -> runIncrementalSync(session);
            };
        } catch (Throwable t) {
            logger.errorf(t, "Error occurred during %s users-sync in realm %s and user provider %s",  syncMode, realmId, providerId);
        }

        return SynchronizationResult.empty();
    }

    /**
     * Schedules the sync task taking the last sync time into consideration to compute the initial delay.
     * If no previous sync has occurred, the initial delay defaults to the full period.
     */
    boolean schedule(KeycloakSession session) {
        UserStorageProviderModel provider = getStorageModel(session);

        if (isSchedulable(provider)) {
            TimerProvider timer = session.getProvider(TimerProvider.class);

            if (timer == null) {
                logger.debugf("Timer provider not available. Not scheduling periodic sync task for provider '%s' in realm '%s'", provider.getName(), realmId);
                return false;
            }

            long periodMillis = period * 1000L;
            long initialDelayMillis = computeInitialDelay(provider, periodMillis);

            logger.debugf("Scheduling user periodic sync task '%s' for user storage provider '%s' in realm '%s' with initial delay %d ms and period %d seconds",
                    getTaskName(), provider.getName(), realmId, initialDelayMillis, period);
            timer.scheduleTask(this, initialDelayMillis, periodMillis);

            return true;
        }

        logger.debugf("Not scheduling periodic sync settings for provider '%s' in realm '%s'", provider.getName(), realmId);

        return false;
    }

    void cancel(KeycloakSession session) {
        TimerProvider timer = session.getProvider(TimerProvider.class);

        if (timer == null) {
            logger.debugf("Timer provider not available. Not cancelling periodic sync task for provider id '%s' in realm '%s'", providerId, realmId);
            return;
        }

        UserStorageProviderModel provider = getStorageModel(session);

        logger.debugf("Cancelling any running user periodic sync task '%s' for user storage provider provider '%s' in realm '%s'", getTaskName(), provider.getName(), realmId);

        TimerTaskContext existingTask = timer.cancelTask(getTaskName());

        if (existingTask != null) {
            logger.debugf("Cancelled periodic sync task with task-name '%s' for provider with id '%s' and name '%s'",
                    getTaskName(), provider.getId(), provider.getName());
        }
    }

    private UserStorageProviderModel getStorageModel(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();

        if (realm == null) {
            throw new ModelIllegalStateException("Realm with id " + realmId + " not found");
        }

        ComponentModel component = realm.getComponent(providerId);

        if (component == null) {
            cancel(session);
            throw new ModelIllegalStateException("User storage provider with id " + providerId + " not found in realm " + realm.getName());
        }

        return new UserStorageProviderModel(component);
    }

    private SynchronizationResult runFullSync(KeycloakSession session) {
        return runSync(session,
                (sf, storage, model) -> storage.sync(sf, realmId, model));
    }

    private SynchronizationResult runIncrementalSync(KeycloakSession session) {
        return runSync(session, (sf, storage, model) -> {
            // See when we did last sync.
            int oldLastSync = model.getLastSync();
            return storage.syncSince(Time.toDate(oldLastSync), sf, realmId, model);
        });
    }

    private SynchronizationResult runSync(KeycloakSession session, TriFunction<KeycloakSessionFactory, ImportSynchronization, UserStorageProviderModel, SynchronizationResult> syncFunction) {
        UserStorageProviderModel provider = getStorageModel(session);
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        ImportSynchronization factory = getProviderFactory(session, provider);

        if (factory == null) {
            return SynchronizationResult.ignored();
        }

        ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);
        // shared key for "full" and "changed" . Improve if needed
        String taskKey = provider.getId() + "::sync";
        // 30 seconds minimal timeout for now
        int timeout = Math.max(TASK_EXECUTION_TIMEOUT, period);

        ExecutionResult<SynchronizationResult> task = clusterProvider.executeIfNotExecuted(taskKey, timeout, () -> {
            // Need to load component again in this transaction for updated data
            SynchronizationResult result = syncFunction.apply(sessionFactory, factory, provider);

            if (!result.isIgnored()) {
                updateLastSyncInterval(session);
            }

            return result;
        });

        SynchronizationResult result = task.getResult();

        if (result == null || !task.isExecuted()) {
            logger.debugf("syncing users for federation provider %s was ignored as it's already in progress", provider.getName());
            return SynchronizationResult.ignored();
        }

        return result;
    }

    private ImportSynchronization getProviderFactory(KeycloakSession session, UserStorageProviderModel provider) {
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        UserStorageProviderFactory<?> factory = (UserStorageProviderFactory<?>) sessionFactory.getProviderFactory(UserStorageProvider.class, provider.getProviderId());

        if (factory instanceof ImportSynchronization f) {
            return f;
        }

        return null;
    }

    // Update interval of last sync for given UserFederationProviderModel. Do it in separate transaction
    private void updateLastSyncInterval(KeycloakSession session) {
        UserStorageProviderModel provider = getStorageModel(session);

        // Update persistent provider in DB
        provider.setLastSync(Time.currentTime(), syncMode);

        RealmModel realm = session.getContext().getRealm();

        realm.updateComponent(provider);
    }

    /**
     * Computes the initial delay for the first timer tick based on when the last sync happened.
     * If no sync has happened yet, the full period is used. Otherwise, the delay is set to the
     * remaining time since the last sync, so that a restarted or newly joined node does not
     * wait a full period unnecessarily.
     */
    private long computeInitialDelay(UserStorageProviderModel provider, long periodMillis) {
        int lastSyncTime = provider.getLastSync(syncMode);

        if (lastSyncTime <= 0) {
            return periodMillis;
        }

        long elapsedMillis = (Time.currentTime() - lastSyncTime) * 1000L;
        long remainingMillis = periodMillis - elapsedMillis;

        // If the remaining time is non-positive, the sync is already overdue — fire immediately.
        return Math.max(remainingMillis, 1L);
    }

    /**
     * Checks if enough time has passed since the last sync to allow a new sync to proceed. This is a secondary
     * guard (in addition to the cluster lock) to prevent a node whose timer fires shortly after another node
     * just completed a sync from triggering an unnecessary duplicate sync.
     */
    private boolean isSyncPeriod(UserStorageProviderModel provider) {
        int lastSyncTime = provider.getLastSync(syncMode);

        if (lastSyncTime <= 0) {
            return true;
        }

        int currentTime = Time.currentTime();
        int timeSinceLastSync = currentTime - lastSyncTime;

        // The threshold must be smaller than the period so legitimate periodic syncs are not
        // skipped. Using min(TASK_EXECUTION_TIMEOUT, period / 2) filters out near-duplicate
        // syncs from cluster nodes with slightly different clocks while scaling correctly
        // for both large and small sync periods.
        int threshold = Math.min(TASK_EXECUTION_TIMEOUT, period / 2);
        return timeSinceLastSync > threshold;
    }

    private boolean isSchedulable(UserStorageProviderModel provider) {
        return provider.isImportEnabled() && provider.isEnabled() && period > 0;
    }
}
