import { Alert } from "antd";
import { getRes } from "../utils/constants";

const XxsTips = () => {
    return (
        <Alert
            type={"warning"}
            showIcon={true}
            style={{ marginBottom: 20, padding: 12 }}
            description={getRes().websiteOther.security.xssTips}
        />
    );
};

export default XxsTips;
