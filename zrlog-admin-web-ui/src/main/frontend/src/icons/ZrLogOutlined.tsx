type ZrLogOutlinedProps = {
    size?: number | string;
};

const ZrLogOutlined = ({ size = "1em" }: ZrLogOutlinedProps) => {
    return (
        <span
            className={"anticon"}
            style={{
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                lineHeight: 0,
            }}
        >
            <svg width={size} height={size} viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="15%" stop-color="rgb(140, 181, 75)" />
                        <stop offset="70%" stop-color="rgb(255,193,7,90%)" />
                    </linearGradient>
                    <clipPath id="clip">
                        <path d="M256,256 L256,0 A200,500 2 1,1 106,423 Z" fill="white" />
                    </clipPath>
                </defs>
                <circle cx="256" cy="256" r="200" stroke="#E8EFF2" stroke-width="50" fill="none" />
                <circle
                    cx="256"
                    cy="256"
                    r="200"
                    stroke="url(#grad)"
                    stroke-width="50"
                    fill="none"
                    clip-path="url(#clip)"
                />
                <g transform="rotate(45,256,256)">
                    <rect x="232" y="140" width="48" height="210" fill="#FFC107" />
                    <polygon points="232,350 280,350 256,390" fill="#DDAA77" />
                    <polygon points="251,380 261,380 256,398" fill="#6E6E6E" />
                    <rect x="232" y="110" width="48" height="30" fill="#FF6B6B" />
                    <rect x="232" y="140" width="48" height="15" fill="#B0B0B0" />
                </g>
            </svg>
        </span>
    );
};

export default ZrLogOutlined;
