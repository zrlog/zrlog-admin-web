import { FunctionComponent, useEffect, useRef, useState } from "react";
import { App, Button, Col, message, Progress, Row, Steps } from "antd";
import Title from "antd/es/typography/Title";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { AxiosError } from "axios";
import { getContextPath } from "../utils/helpers";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getCsrData, getVersion } from "../api";
import { UpgradeData } from "../type";
import UpgradeContent from "./upgrade-content";
import { markdownToHtml } from "@editor/dist/src/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";
import { getAppState } from "../base/ConfigProviderApp";

export const API_VERSION_PATH = "/api/public/version";
export const API_DO_UPGRADE_PATH = "/api/admin/upgrade/doUpgrade";

type UpgradeState = {
    current: number;
    downloadProcess: number;
    upgradeMessage: string;
};

type StepInfo = {
    title: string;
    alias: "changeLog" | "downloadProcess" | "doUpgrade";
};

export type UpgradeProps = {
    data: UpgradeData;
    offline: boolean;
    offlineData: boolean;
};

const Upgrade: FunctionComponent<UpgradeProps> = ({ data, offline, offlineData }) => {
    const preUpgradeKey = data.preUpgradeKey;
    const isDisabledDownload = () => {
        return !data.onlineUpgradable;
    };
    const steps: StepInfo[] = isDisabledDownload()
        ? [
              {
                  title: getRes().upgrade.changeLog,
                  alias: "changeLog",
              },
              {
                  title: getRes().upgrade.execute,
                  alias: "doUpgrade",
              },
          ]
        : [
              {
                  title: getRes().upgrade.changeLog,
                  alias: "changeLog",
              },
              {
                  title: getRes().upgrade.download,
                  alias: "downloadProcess",
              },
              {
                  title: getRes().upgrade.execute,
                  alias: "doUpgrade",
              },
          ];

    const upgradeTimer = useRef<NodeJS.Timeout | null>(null);

    const [state, setState] = useState<UpgradeState>({
        current: 0,
        downloadProcess: 0,
        upgradeMessage: "",
    });

    const { modal } = App.useApp();

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const checkRestartSuccess = (newBuildId: string) => {
        getVersion(newBuildId, axiosInstance)
            .then(({ data }) => {
                if (newBuildId === data.buildId) {
                    modal.success({
                        title: data.message,
                        content: "",
                        onOk: function () {
                            window.location.href = getRealRouteUrl(
                                getContextPath() + "admin/index?buildId=" + newBuildId
                            );
                        },
                    });
                    return;
                }
                upgradeTimer.current = setTimeout(() => {
                    checkRestartSuccess(newBuildId);
                }, 500);
            })
            .catch(() => {
                upgradeTimer.current = setTimeout(() => {
                    checkRestartSuccess(newBuildId);
                }, 500);
            });
    };

    const axiosInstance = useAxiosBaseInstance();

    const downloadProcess = async () => {
        const current = 1;
        setState((prevState) => {
            return {
                ...prevState,
                current: current,
            };
        });
        try {
            const data = await getCsrData("/upgrade/download?preUpgradeKey=" + preUpgradeKey, 0, axiosInstance);
            if (data.error) {
                messageApi.error(data.message);
                return;
            }
            setState((prevState) => {
                return {
                    ...prevState,
                    downloadProcess: data.data.process,
                    current: current,
                };
            });
            if (data.data.process < 100) {
                upgradeTimer.current = setTimeout(downloadProcess, 500);
            }
        } catch (e) {
            if (e instanceof AxiosError) {
                if (e.response && e.response.data) {
                    messageApi.error(e.response.data.message);
                }
            }
        }
    };

    const newBuildId = data.version.buildId;

    const upgrade = async () => {
        const current = 2;
        setState((prevState) => {
            return {
                ...prevState,
                current: current,
            };
        });
        try {
            const { data } = await getCsrData("/upgrade/doUpgrade?preUpgradeKey=" + preUpgradeKey, 0, axiosInstance);
            if (data && data.message) {
                const htmlContent = await markdownToHtml(data.message);
                setState((prevState) => {
                    return {
                        ...prevState,
                        upgradeMessage: htmlContent,
                        current: current,
                    };
                });
            }
            if (data && !data.finish) {
                upgradeTimer.current = setTimeout(upgrade, 500);
                return;
            }
            checkRestartSuccess(newBuildId);
        } catch (e) {
            console.error(e);
            //need restart check
            checkRestartSuccess(newBuildId);
        }
    };

    const next = async () => {
        if (state.current === 0) {
            if (isDisabledDownload()) {
                await upgrade();
            } else {
                await downloadProcess();
            }
        } else if (state.current === 1) {
            await upgrade();
        }
    };

    const nextDisabled = (): boolean => {
        if (offlineData) {
            return true;
        }
        if (offline) {
            return true;
        }
        if (!data.upgrade) {
            return true;
        }
        if (state.current === 1) {
            return state.downloadProcess < 100;
        }
        return false;
    };

    useEffect(() => {
        return () => {
            if (upgradeTimer && upgradeTimer.current) {
                clearTimeout(upgradeTimer.current);
            }
        };
    }, []);

    return (
        <Row key={data.preUpgradeKey}>
            {contextHolder}
            <Col style={{ maxWidth: 600 }} xs={24}>
                <Steps current={state.current} style={{ paddingTop: 16 }} items={steps} />
                <div className="steps-content" style={{ marginTop: 20 }}>
                    {state.current === 0 && (
                        <>
                            <Title level={4}>{getRes().upgrade.changeLog}</Title>
                            <UpgradeContent data={data} />
                        </>
                    )}
                    {state.current === 1 && (
                        <>
                            <Title level={4}>{getRes().upgrade.downloadingPackage}</Title>
                            <Progress strokeLinecap="round" percent={state.downloadProcess} />
                        </>
                    )}
                    {state.current === 2 && (
                        <>
                            <Title level={4}>{getRes().upgrade.executing}</Title>
                            <HtmlPreviewPanel dark={getAppState().dark} htmlContent={state.upgradeMessage} />
                        </>
                    )}
                </div>
                <div className="steps-action" style={{ paddingTop: 20 }}>
                    {state.current < steps.length - 1 && (
                        <Button type="primary" loading={offlineData} disabled={nextDisabled()} onClick={() => next()}>
                            {getRes().upgrade.nextStep}
                        </Button>
                    )}
                </div>
            </Col>
        </Row>
    );
};

export default Upgrade;
