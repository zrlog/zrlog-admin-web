import { SafetyCertificateOutlined } from "@ant-design/icons";
import { Alert, Descriptions, Space, Tag, Typography } from "antd";
import { useTheme } from "antd-style";
import { Link } from "react-router-dom";
import { BackupProtectionStatus, UpgradeData } from "../type";
import { getRealRouteUrl, getRes } from "../utils/constants";
import {
    resolveBackupProtectionStatus,
    resolveUpgradeRuntimeModes,
    UpgradeRuntimeMode,
} from "./upgrade-readiness-utils";

const UpgradeReadiness = ({ data }: { data: UpgradeData }) => {
    const theme = useTheme();
    const res = getRes().upgrade.maintenance;
    const backupRes = getRes().upgrade.backupProtection;
    const backupProtection = data.backupProtection;
    const runtimeModeLabels: Record<UpgradeRuntimeMode, string> = res.runtimeMode;
    const formatEvidenceTime = (value?: number) =>
        typeof value === "number" && Number.isFinite(value) ? new Date(value).toLocaleString() : backupRes.notAvailable;
    const renderEvidence = (
        timestamp?: number,
        file?: string,
        sha256?: string,
        verificationSuccess?: boolean,
        verificationMessage?: string
    ) => (
        <Space direction="vertical" size={0} style={{ maxWidth: "100%", minWidth: 0 }}>
            <Typography.Text type="secondary">
                {backupRes.time}: {formatEvidenceTime(timestamp)}
            </Typography.Text>
            <Typography.Text style={{ wordBreak: "break-word" }}>
                {backupRes.file}: {file || backupRes.notAvailable}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ wordBreak: "break-all" }}>
                {backupRes.sha256}:{" "}
                <span style={{ fontFamily: "monospace", fontSize: theme.fontSizeSM }}>
                    {sha256 || backupRes.notAvailable}
                </span>
            </Typography.Text>
            {typeof verificationSuccess === "boolean" && (
                <Space size="small" wrap>
                    <Typography.Text type="secondary">{backupRes.verificationResult}:</Typography.Text>
                    <Tag color={verificationSuccess ? "success" : "error"}>
                        {verificationSuccess ? backupRes.verificationPassed : backupRes.verificationFailed}
                    </Tag>
                </Space>
            )}
            {verificationMessage && (
                <Typography.Text type="secondary" style={{ wordBreak: "break-word" }}>
                    {backupRes.verificationDetail}: {verificationMessage}
                </Typography.Text>
            )}
        </Space>
    );
    const renderBackupProtection = (status?: BackupProtectionStatus) => {
        const statusKey = resolveBackupProtectionStatus(status);
        const ready = statusKey === "READY";
        return (
            <Alert
                type={ready ? "success" : "warning"}
                showIcon
                message={
                    <Space size="small" wrap>
                        <span>{backupRes.title}</span>
                        <Tag color={ready ? "success" : "warning"}>{ready ? backupRes.ready : backupRes.warning}</Tag>
                    </Space>
                }
                description={
                    <Space direction="vertical" size="small" style={{ width: "100%" }}>
                        <Typography.Text>{backupRes.status[statusKey]}</Typography.Text>
                        <Typography.Text type="secondary">
                            {backupRes.recommendationLabel}: {backupRes.recommendation[statusKey]}
                        </Typography.Text>
                        <Descriptions size="small" column={1}>
                            <Descriptions.Item label={backupRes.lastBackup}>
                                {renderEvidence(status?.lastBackupAt, status?.lastBackupFile, status?.lastBackupSha256)}
                            </Descriptions.Item>
                            <Descriptions.Item label={backupRes.lastVerification}>
                                {renderEvidence(
                                    status?.lastVerifiedAt,
                                    status?.lastVerifiedFile,
                                    status?.lastVerifiedSha256,
                                    status?.verificationSuccess,
                                    status?.verificationMessage
                                )}
                            </Descriptions.Item>
                        </Descriptions>
                    </Space>
                }
            />
        );
    };

    return (
        <section aria-label={res.title} style={{ marginTop: theme.marginLG }}>
            <div style={{ alignItems: "center", display: "flex", gap: 8, marginBottom: theme.marginSM }}>
                <SafetyCertificateOutlined />
                <Typography.Text strong>{res.title}</Typography.Text>
            </div>
            <Descriptions size="small" column={{ xs: 1, sm: 2 }} style={{ marginBottom: theme.marginSM }}>
                <Descriptions.Item label={res.runtime}>
                    <Space size={4} wrap>
                        {resolveUpgradeRuntimeModes(data).map((mode) => (
                            <Tag key={mode}>{runtimeModeLabels[mode]}</Tag>
                        ))}
                    </Space>
                </Descriptions.Item>
                <Descriptions.Item label={res.updateMethod}>
                    <Space size="small" wrap>
                        <span>{data.onlineUpgradable ? res.onlineUpdate : res.manualUpdate}</span>
                        <Link to={getRealRouteUrl("/system")}>{res.viewSystem}</Link>
                    </Space>
                </Descriptions.Item>
            </Descriptions>
            {renderBackupProtection(backupProtection)}
        </section>
    );
};

export default UpgradeReadiness;
