import { CSSProperties, FunctionComponent } from "react";
import { Tag } from "antd";
import { TagOutlined } from "@ant-design/icons";
import { getAppState } from "../base/ConfigProviderApp";

type TagsProps = {
    keywords: string;
    closeable?: boolean;
    tagStyle?: CSSProperties;
    onClick?: (e: any) => void;
    onClose?: (e: any, tag: string) => void;
};

const Tags: FunctionComponent<TagsProps> = ({ keywords, closeable, tagStyle, onClick, onClose }) => {
    const buildTag = (tag: string) => {
        if (tag === null || tag.trim() === "") {
            return <></>;
        }
        return (
            <Tag
                icon={<TagOutlined />}
                closable={closeable}
                onClick={onClick}
                onClose={(e) => {
                    if (onClose) {
                        onClose(e, tag);
                    }
                }}
                color={getAppState().colorPrimary}
                style={{ userSelect: "none", marginRight: 0, ...tagStyle }}
            >
                {tag.trim()}
            </Tag>
        );
    };

    return (
        <span key={"all-" + keywords} style={{ display: "flex", gap: 4, whiteSpace: "normal", flexFlow: "wrap" }}>
            {keywords.split(",").map(buildTag)}
        </span>
    );
};

export default Tags;
