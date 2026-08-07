import { RightOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useTheme } from "antd-style";
import { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";

type DashboardCardActionProps =
    | {
          children: ReactNode;
          to: string;
          onClick?: never;
      }
    | {
          children: ReactNode;
          to?: never;
          onClick: () => void;
      };

const DashboardCardAction = (props: DashboardCardActionProps) => {
    const theme = useTheme();
    const actionStyle: CSSProperties = {
        display: "flex",
        alignItems: "center",
        gap: 6,
        color: theme.colorText,
        whiteSpace: "nowrap",
    };
    const content = (
        <>
            <span>{props.children}</span>
            <RightOutlined />
        </>
    );

    if (props.to !== undefined) {
        return (
            <Link to={props.to} style={actionStyle}>
                {content}
            </Link>
        );
    }

    return (
        <Button
            type="link"
            onClick={props.onClick}
            style={{
                ...actionStyle,
                border: 0,
                height: "auto",
                padding: 0,
                fontFamily: "inherit",
                fontSize: "inherit",
                fontWeight: "inherit",
                lineHeight: "inherit",
            }}
        >
            {content}
        </Button>
    );
};

export default DashboardCardAction;
