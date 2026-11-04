import type { I18nShape } from "../types";

export const zhCN = {
    websiteAdmin: {
        title: "管理设置",
        backendServer: {
            label: "后端服务地址",
            help: "用于应用授权、MCP 和通知连接，需包含部署子路径。留空使用部署配置或博客地址",
            changeHelp:
                "连接客户端会使用此地址，请填写对外入口而非内部源站。更改地址后，原有应用授权和个人令牌需要重新建立",
            placeholder: "https://admin.example.com",
            invalid: "请输入完整的 HTTPS 地址，不含查询参数、片段或账号信息；本机调试可使用 HTTP",
        },
        summary:
            "设置站点后端地址，以及后台的默认语言、外观、每页文章数和登录有效时长。成员可在偏好设置中调整自己的语言、外观、每页文章数和登录有效时长",
        theme: {
            label: "后台主题",
            option: {
                default: "默认",
                desk: "Desk",
                antd: "Ant Design",
                bootstrap: "Bootstrap",
                geek: "极客",
                cartoon: "卡通",
                glass: "玻璃",
                shadcn: "Shadcn",
                illustration: "插画",
            },
        },
        dark: {
            mode: "深色模式",
        },
        compact: {
            mode: "紧凑模式",
        },
        color: {
            primary: "主题主色",
            preset: {
                azureBlue: "拂晓蓝",
                skyBlue: "冰川蓝",
                deepBlue: "深海蓝",
                indigoBlue: "星夜蓝",
                violetPurple: "鸢尾紫",
                deepPurple: "暮光紫",
                midPurple: "葡萄紫",
                magentaPink: "落樱粉",
                fuchsiaPink: "玫影粉",
                cyanTeal: "海湾青",
                iceBlue: "霜空蓝",
                deepTeal: "翡翠青",
                green: "森林绿",
                lightGreen: "薄荷绿",
                yellowGreen: "春芽绿",
                lime: "电光绿",
                brightYellow: "日曜黄",
                lemonYellow: "柠光黄",
                goldenYellow: "香槟金",
                mustardYellow: "琥珀金",
                orange: "熔岩橙",
                amberOrange: "琥珀橙",
                orangeRed: "霞焰红",
                burntOrange: "枫叶橙",
                red: "烈焰红",
                tomatoRed: "珊瑚红",
                brown: "胡桃棕",
                slateBlueGray: "星岩灰",
                charcoalGray: "曜石黑",
            },
        },
        staticResource: {
            url: "管理页静态资源 URL",
            urlTips: "留空使用本站静态资源",
            urlHelp: "可填写 http:// 或 https:// 开头的静态资源根地址。配置错误会导致后台样式或脚本加载失败",
        },
        session: {
            timeout: "默认登录有效时长",
            timeoutHelp:
                "范围为 6–99999 分钟，供未设置个人登录时长的账号使用。后台操作会延续登录有效期，修改从下次登录生效",
            timeoutUnit: "分钟",
        },
        language: {
            label: "语言",
            chinese: "简体中文",
            english: "English",
        },
        moreSettings: "文章管理设置",
        basicSettings: "后台基础设置",
        appearancePreviewHint: "编辑外观时临时预览站点默认效果，保存后才修改站点配置；离开此页后按个人偏好显示",
        article: {
            pageSize: "后台文章每页数量",
            pageSizeTip: "后台文章列表的默认每页数量，供未设置个人每页数量的账号使用",
        },
        pwa: {
            title: "后台 PWA 图标",
            icon192: "后台 PWA 图标（192px）",
            icon192Help: "用于后台管理页安装到主屏幕或桌面后的应用图标，不影响前台站点图标",
            icon512: "后台 PWA 图标（512px）",
            icon512Help: "用于后台管理页在高分辨率设备或启动画面相关场景中的应用图标，不影响前台站点图标",
        },
    },
} as const;

export const enUS: I18nShape<typeof zhCN> = {
    websiteAdmin: {
        title: "Admin Settings",
        backendServer: {
            label: "Backend service URL",
            help: "Used for authorization, MCP and notifications. Include the deployment path. Leave empty to use deployment settings or the blog URL",
            changeHelp:
                "Clients use this URL, so enter the external gateway rather than an internal origin. Changing it requires new application authorizations and personal tokens",
            placeholder: "https://admin.example.com",
            invalid:
                "Enter a full HTTPS URL without a query, fragment or credentials; HTTP is allowed for local development",
        },
        summary:
            "Set the backend service URL and the default admin language, appearance, articles per page and session duration. Members can choose their own language, appearance, articles per page and session duration in Preferences",
        theme: {
            label: "Admin theme",
            option: {
                default: "Default",
                desk: "Desk",
                antd: "Ant Design",
                bootstrap: "Bootstrap",
                geek: "Geek",
                cartoon: "Cartoon",
                glass: "Glass",
                shadcn: "Shadcn",
                illustration: "Illustration",
            },
        },
        dark: {
            mode: "Dark Mode",
        },
        compact: {
            mode: "Compact Mode",
        },
        color: {
            primary: "Primary Theme Color",
            preset: {
                azureBlue: "Dawn Blue",
                skyBlue: "Glacier Blue",
                deepBlue: "Deep Sea Blue",
                indigoBlue: "Starlit Blue",
                violetPurple: "Iris Violet",
                deepPurple: "Twilight Purple",
                midPurple: "Grape Purple",
                magentaPink: "Sakura Pink",
                fuchsiaPink: "Rose Glow",
                cyanTeal: "Lagoon Teal",
                iceBlue: "Frost Blue",
                deepTeal: "Emerald Teal",
                green: "Forest Green",
                lightGreen: "Mint Green",
                yellowGreen: "Spring Bud",
                lime: "Neon Lime",
                brightYellow: "Sunburst Yellow",
                lemonYellow: "Lemon Glow",
                goldenYellow: "Champagne Gold",
                mustardYellow: "Amber Gold",
                orange: "Lava Orange",
                amberOrange: "Amber Orange",
                orangeRed: "Sunset Ember",
                burntOrange: "Maple Orange",
                red: "Flame Red",
                tomatoRed: "Coral Red",
                brown: "Walnut Brown",
                slateBlueGray: "Meteor Gray",
                charcoalGray: "Obsidian Black",
            },
        },
        staticResource: {
            url: "Admin Static Assets URL",
            urlTips: "Leave blank to use this site’s static assets",
            urlHelp:
                "Enter an http:// or https:// static asset base URL. A bad value can cause admin styles or scripts to fail to load",
        },
        session: {
            timeout: "Default Session Duration",
            timeoutHelp:
                "Allowed range: 6–99999 minutes. Used by accounts without a personal session duration. Admin activity renews the session; changes apply at the next sign-in",
            timeoutUnit: "Minutes",
        },
        language: {
            label: "Language",
            chinese: "简体中文",
            english: "English",
        },
        moreSettings: "Article Management Settings",
        basicSettings: "Admin Basic Settings",
        appearancePreviewHint:
            "Appearance edits temporarily preview site defaults. Save to update site settings; leaving this page restores your personal appearance",
        article: {
            pageSize: "Articles Per Page in Admin",
            pageSizeTip:
                "Default number of articles per page in the admin list, used by accounts without a personal page size",
        },
        pwa: {
            title: "Admin PWA Icons",
            icon192: "Admin PWA Icon (192px)",
            icon192Help:
                "Used as the app icon when the admin area is added to the home screen or desktop. It does not affect the public site icon",
            icon512: "Admin PWA Icon (512px)",
            icon512Help:
                "Used by the admin area on high-resolution devices and splash-related scenarios. It does not affect the public site icon",
        },
    },
};
