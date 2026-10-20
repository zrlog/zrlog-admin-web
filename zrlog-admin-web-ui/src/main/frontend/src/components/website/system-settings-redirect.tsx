import {Navigate, useLocation} from "react-router-dom";
import {getRealRouteUrl} from "../../utils/constants";

const SystemSettingsRedirect = ({page}: { page: "upgrade" | "version" }) => {
    const location = useLocation();
    return <Navigate replace to={getRealRouteUrl("/system/" + page + location.search) + location.hash}/>;
};

export default SystemSettingsRedirect;
