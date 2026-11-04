package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.UpgradeCheckResponse;
import com.zrlog.business.rest.response.BackupProtectionStatus;
import com.zrlog.util.I18nUtil;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Localizes the shared readiness data at the authenticated admin response boundary. */
public final class UpgradeReadinessMessages {
    private UpgradeReadinessMessages() {}

    public static void apply(UpgradeCheckResponse response) {
        BackupProtectionStatus backup = response.getBackupProtection();
        String key = "invalidEvidence";
        if (backup != null
                && backup.getStatus() != null
                && Objects.equals(
                        backup.getReady(), BackupProtectionStatus.READY.equals(backup.getStatus()))
                && (!BackupProtectionStatus.BACKUP_STALE.equals(backup.getStatus())
                        || (backup.getBackupMaxAgeMillis() != null
                                && backup.getBackupMaxAgeMillis() > 0))
                && (!BackupProtectionStatus.VERIFICATION_STALE.equals(backup.getStatus())
                        || (backup.getVerificationMaxAgeMillis() != null
                                && backup.getVerificationMaxAgeMillis() > 0))) {
            switch (backup.getStatus()) {
                case BackupProtectionStatus.READY:
                    key = "ready";
                    break;
                case BackupProtectionStatus.MISSING_BACKUP:
                    key = "missingBackup";
                    break;
                case BackupProtectionStatus.BACKUP_STALE:
                    key = "backupStale";
                    break;
                case BackupProtectionStatus.MISSING_VERIFICATION:
                    key = "missingVerification";
                    break;
                case BackupProtectionStatus.VERIFICATION_FAILED:
                    key = "verificationFailed";
                    break;
                case BackupProtectionStatus.VERIFICATION_STALE:
                    key = "verificationStale";
                    break;
                case BackupProtectionStatus.BACKUP_CHANGED_AFTER_VERIFICATION:
                    key = "backupChangedAfterVerification";
                    break;
                default:
                    break;
            }
        }
        String message =
                I18nUtil.getAdminBackendStringFromRes("admin.upgrade.backup.status." + key);
        if (backup != null) {
            if (backup.getBackupMaxAgeMillis() != null)
                message =
                        message.replace(
                                "{hours}",
                                Long.toString(
                                        TimeUnit.MILLISECONDS.toHours(
                                                backup.getBackupMaxAgeMillis())));
            if (backup.getVerificationMaxAgeMillis() != null)
                message =
                        message.replace(
                                "{days}",
                                Long.toString(
                                        TimeUnit.MILLISECONDS.toDays(
                                                backup.getVerificationMaxAgeMillis())));
        }
        response.setBackupProtectionMessage(message);
        response.setBackupProtectionRecommendation(
                I18nUtil.getAdminBackendStringFromRes(
                        "admin.upgrade.backup.recommendation." + key));
    }
}
