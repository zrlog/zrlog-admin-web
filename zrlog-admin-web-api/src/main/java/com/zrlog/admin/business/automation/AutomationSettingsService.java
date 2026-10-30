package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Dedicated trigger credentials are stored as digests and never included in ordinary page data. */
public final class AutomationSettingsService {
    static final String KEY = "assistant.scheduler";
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final LongSupplier clock;
    private final BooleanSupplier faas;

    public AutomationSettingsService(AutomationStore store, TaskAuthority authority, LongSupplier clock, BooleanSupplier faas) {
        this.store = store;
        this.authority = authority;
        this.clock = clock;
        this.faas = faas;
    }

    public SettingsView read(AccountAccess actor) throws SQLException {
        authorize(actor);
        return view(readStored());
    }

    public SettingsView save(AccountAccess actor, long revision, boolean internalEnabled, boolean externalEnabled) throws SQLException {
        return save(actor, revision, internalEnabled, externalEnabled, null);
    }

    public SettingsView save(AccountAccess actor, long revision, boolean internalEnabled, boolean externalEnabled,
                             Integer externalBudgetSeconds) throws SQLException {
        actor = authorize(actor);
        AutomationStore.Snapshot<Settings> row = store.read(KEY, Settings.class);
        Settings settings = row == null ? new Settings() : row.copy(Settings.class);
        if (settings.revision != revision) throw new IllegalStateException("automation.settingsChanged");
        if (externalEnabled && settings.tokenHash == null) throw new IllegalArgumentException("automation.tokenRequired");
        if (externalBudgetSeconds != null) {
            if (externalBudgetSeconds < 10 || externalBudgetSeconds > 660) {
                throw new IllegalArgumentException("automation.invalidBudget");
            }
            settings.externalBudgetSeconds = externalBudgetSeconds;
        }
        settings.revision++;
        settings.internalEnabled = internalEnabled;
        settings.externalEnabled = externalEnabled;
        if (!store.writeAuthorized(KEY, row, settings, actor)) throw new IllegalStateException("automation.settingsChanged");
        return view(settings);
    }

    public IssuedToken rotate(AccountAccess actor, long revision) throws SQLException {
        actor = authorize(actor);
        AutomationStore.Snapshot<Settings> row = store.read(KEY, Settings.class);
        Settings settings = row == null ? new Settings() : row.copy(Settings.class);
        if (settings.revision != revision) throw new IllegalStateException("automation.settingsChanged");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String secret = "zra_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        settings.revision++;
        settings.tokenHash = digest(secret);
        settings.tokenId = UUID.randomUUID().toString();
        settings.tokenCreatedAt = clock.getAsLong();
        settings.externalEnabled = true;
        if (!store.writeAuthorized(KEY, row, settings, actor)) throw new IllegalStateException("automation.settingsChanged");
        IssuedToken result = new IssuedToken();
        result.token = secret;
        result.settings = view(settings);
        return result;
    }

    public SettingsView revoke(AccountAccess actor, long revision) throws SQLException {
        actor = authorize(actor);
        AutomationStore.Snapshot<Settings> row = store.read(KEY, Settings.class);
        Settings settings = row == null ? new Settings() : row.copy(Settings.class);
        if (settings.revision != revision) throw new IllegalStateException("automation.settingsChanged");
        settings.revision++;
        settings.tokenHash = null;
        settings.tokenId = null;
        settings.tokenCreatedAt = null;
        settings.externalEnabled = false;
        if (!store.writeAuthorized(KEY, row, settings, actor)) throw new IllegalStateException("automation.settingsChanged");
        return view(settings);
    }

    public String authenticate(String authorization) throws SQLException {
        if (authorization == null || authorization.length() > 128) throw new PermissionErrorException();
        String[] parts = authorization.trim().split("\\s+");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("Bearer")
                || !parts[1].matches("zra_[A-Za-z0-9_-]{43}")) throw new PermissionErrorException();
        Settings settings = readStored();
        if (!settings.externalEnabled || settings.tokenHash == null || !MessageDigest.isEqual(
                settings.tokenHash.getBytes(StandardCharsets.US_ASCII), digest(parts[1]).getBytes(StandardCharsets.US_ASCII))) {
            throw new PermissionErrorException();
        }
        return settings.tokenId;
    }

    public boolean internalEnabled() throws SQLException { return !faas.getAsBoolean() && readStored().internalEnabled; }

    public long externalBudgetMillis() throws SQLException { return readStored().externalBudgetSeconds * 1000L; }

    public long manualBudgetMillis() throws SQLException { return faas.getAsBoolean() ? externalBudgetMillis() : 660000; }

    private Settings readStored() throws SQLException {
        AutomationStore.Snapshot<Settings> row = store.read(KEY, Settings.class);
        return row == null ? new Settings() : row.value;
    }

    private AccountAccess authorize(AccountAccess actor) {
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        return actor;
    }

    private SettingsView view(Settings settings) {
        SettingsView view = new SettingsView();
        view.revision = settings.revision;
        view.internalEnabled = settings.internalEnabled;
        view.externalEnabled = settings.externalEnabled;
        view.tokenConfigured = settings.tokenHash != null;
        view.tokenCreatedAt = settings.tokenCreatedAt;
        view.faas = faas.getAsBoolean();
        view.triggerPath = AdminConstants.ADMIN_ASSISTANT_TASK_TRIGGER_API_URI_PATH;
        view.externalBudgetSeconds = settings.externalBudgetSeconds;
        return view;
    }

    private static String digest(String token) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public static class Settings {
        public long revision;
        public boolean internalEnabled = true;
        public boolean externalEnabled;
        public int externalBudgetSeconds = 60;
        public String tokenHash;
        public String tokenId;
        public Long tokenCreatedAt;
    }

    public static class SettingsView {
        public long revision;
        public boolean internalEnabled;
        public boolean externalEnabled;
        public int externalBudgetSeconds;
        public boolean tokenConfigured;
        public Long tokenCreatedAt;
        public boolean faas;
        public String triggerPath;
    }

    public static class IssuedToken {
        public String token;
        public SettingsView settings;
    }
}
