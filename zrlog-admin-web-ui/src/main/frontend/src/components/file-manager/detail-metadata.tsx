import Typography from "@zrlog/ui/antd/Typography";
import {Space} from "antd";
import TimeAgo from "@zrlog/editor/dist/editor/TimeAgo";
import {getLabelValueSeparator, getRes} from "../../utils/constants";
import {formatDateTime} from "../../utils/date-time";
import {FileEntry, formatSize} from "./shared";

const {Text} = Typography;

const FileDetailMetadata = ({entry}: {entry: FileEntry}) => {
    const res = getRes().fileManager;
    const uploadedAt = entry.type === "file" ? entry.uploadedAt ?? 0 : 0;

    return (
        <Space direction="vertical" size={4} style={{width: "100%", overflowWrap: "anywhere"}}>
            {entry.type === "file" && entry.originalName && (
                <Text type="secondary">
                    {res.originalName}
                    {getLabelValueSeparator()}
                    {entry.originalName}
                </Text>
            )}
            <Text type="secondary">
                {res.size}
                {getLabelValueSeparator()}
                {formatSize(entry.size)}
            </Text>
            {entry.type === "file" && entry.mimeType && (
                <Text type="secondary">
                    {res.mimeType}
                    {getLabelValueSeparator()}
                    {entry.mimeType}
                </Text>
            )}
            <Text type="secondary">
                {uploadedAt > 0 ? res.uploadedAt : res.lastModified}
                {getLabelValueSeparator()}
                {uploadedAt > 0 ? (
                    <time dateTime={new Date(uploadedAt).toISOString()}>{formatDateTime(uploadedAt)}</time>
                ) : entry.lastModified > 0 ? (
                    <TimeAgo timestamp={entry.lastModified}/>
                ) : (
                    "-"
                )}
            </Text>
        </Space>
    );
};

export default FileDetailMetadata;
