import { Navigate, useLocation } from "react-router-dom";
import { getRealRouteUrl } from "../../utils/constants";

const LegacyVersion = () => {
    const location = useLocation();
    return <Navigate replace to={getRealRouteUrl("/system/version" + location.search) + location.hash} />;
};

export default LegacyVersion;
