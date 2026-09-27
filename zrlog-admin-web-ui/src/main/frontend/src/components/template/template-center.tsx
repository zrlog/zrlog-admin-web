import { Grid } from "antd";

export type TemplateCenterData = {
    url: string;
};

const TemplateCenter = ({ data }: { data: TemplateCenterData }) => {
    const screens = Grid.useBreakpoint();
    const iframeHeight = screens.xl ? 1200 : "calc(100vh - 112px)";
    const iframeMinHeight = screens.md ? 720 : 520;

    return (
        <div style={{ width: "100%", overflowX: "auto" }}>
            <iframe
                title={data.url}
                style={{ border: 0, width: "100%", height: iframeHeight, minHeight: iframeMinHeight, display: "block" }}
                src={data.url}
            />
        </div>
    );
};

export default TemplateCenter;
