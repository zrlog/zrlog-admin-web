package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.rest.response.UpgradeCheckResponse;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.business.rest.response.BackupProtectionStatus;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class UpgradeReadinessMessagesTest {
    @Test
    public void staleDescriptionsUseTheReportedPolicyAndCurrentLanguage() {
        for (String language : List.of("zh_CN", "en_US")) {
            try (var ignored = AdminLanguageContext.open(language)) {
                var backup = new BackupProtectionStatus();
                backup.setReady(false);
                backup.setStatus(BackupProtectionStatus.BACKUP_STALE);
                backup.setBackupMaxAgeMillis(TimeUnit.HOURS.toMillis(48));
                backup.setVerificationMaxAgeMillis(TimeUnit.DAYS.toMillis(10));
                var response = new UpgradeCheckResponse();
                response.setBackupProtection(backup);
                UpgradeReadinessMessages.apply(response);
                assertTrue(response.getBackupProtectionMessage().contains("48"));
                assertTrue(
                        response.getBackupProtectionMessage()
                                .contains(language.equals("zh_CN") ? "小时" : "hours"));
                assertFalse(response.getBackupProtectionMessage().contains("36"));
                assertFalse(response.getBackupProtectionRecommendation().isBlank());
                backup.setStatus(BackupProtectionStatus.VERIFICATION_STALE);
                UpgradeReadinessMessages.apply(response);
                assertTrue(response.getBackupProtectionMessage().contains("10"));
                assertFalse(response.getBackupProtectionMessage().contains("{"));
            }
        }
    }

    @Test
    public void missingOrInconsistentEvidenceNeverProducesAnInventedPolicy() {
        try (var ignored = AdminLanguageContext.open("en_US")) {
            var response = new UpgradeCheckResponse();
            UpgradeReadinessMessages.apply(response);
            String invalid = response.getBackupProtectionMessage();
            assertFalse(invalid.isBlank());
            for (String status :
                    List.of(
                            BackupProtectionStatus.READY,
                            BackupProtectionStatus.BACKUP_STALE,
                            "FUTURE_STATUS")) {
                var backup = new BackupProtectionStatus();
                backup.setStatus(status);
                backup.setReady(false);
                response.setBackupProtection(backup);
                UpgradeReadinessMessages.apply(response);
                assertEquals(invalid, response.getBackupProtectionMessage());
            }
            var missingReady = new BackupProtectionStatus();
            missingReady.setStatus(BackupProtectionStatus.MISSING_BACKUP);
            response.setBackupProtection(missingReady);
            UpgradeReadinessMessages.apply(response);
            assertEquals(invalid, response.getBackupProtectionMessage());
        }
    }
}
