export type AdminLang = "en_US" | "zh_CN";

const zhCN = {
    "goBack": "返回",
    "key": "名称",
    "value": "值",
    "searchTip": "请输入关键字...",
    "globalSearchTip": "全局搜索",
    "index": {
        "title": "控制台",
        "welcome": {
            "systemInfo": "系统信息",
            "viewSystem": "查看系统信息",
            "currentVersion": "当前版本"
        },
        "statistics": "数据概览",
        "status": "内容状态",
        "storage": {
            "disk": "磁盘占用",
            "cache": "缓存占用"
        },
        "audit": {
            "label": "最近操作",
            "more": "查看更多",
            "full": "完整操作审计（最近100条）",
            "empty": "暂无审计日志",
            "crawler": "爬虫"
        },
        "insight": {
            "empty": {
                "category": "暂无分类数据",
                "tag": "暂无标签数据"
            },
            "label": "内容洞察",
            "categoryDistribution": "分类占比",
            "hotTags": "高频标签"
        },
        "activity": "活动",
        "activityGraph": {
            "month": {
                "jan": "一月",
                "feb": "二月",
                "mar": "三月",
                "apr": "四月",
                "may": "五月",
                "jun": "六月",
                "jul": "七月",
                "aug": "八月",
                "sep": "九月",
                "oct": "十月",
                "nov": "十一月",
                "dec": "十二月"
            },
            "weekday": {
                "mon": "一",
                "wed": "三",
                "fri": "五"
            },
            "noData": "无数据"
        },
        "statisticsCard": {
            "todayComment": "今日评论",
            "totalComment": "评论总数",
            "totalArticle": "文章总数",
            "totalArticleView": "文章浏览总数"
        },
        "quickAction": {
            "label": "快捷操作",
            "writeArticle": "写文章",
            "backupFiles": "备份文件",
            "localDraft": "未完成草稿",
            "localDraftTag": "本地缓存",
            "localDraftUntitled": "未命名草稿",
            "localDraftTip": "检测到你上次未完成的本地内容，可以直接回到编辑器继续写作。",
            "continueWriting": "继续写作",
            "clearLocalDraft": "清除本地草稿",
            "clearLocalDraftConfirm": "清除后将无法恢复这份本地草稿，确定继续吗？"
        }
    },
    "article": {
        "title": "文章管理",
        "label": "文章",
        "commentAble": "允许评论",
        "search": "搜索",
        "status": {
            "private": "私密",
            "draft": "草稿",
            "published": "公开"
        },
        "tag": {
            "all": "全部"
        },
        "previewOpenPrefix": "点击查看《",
        "previewOpenSuffix": "》",
        "createTime": "创建时间",
        "lastUpdateDate": "更新时间",
        "viewCount": "浏览量",
        "commentSize": "评论量",
        "listSearchTip": "搜索文章",
        "cover": "封面",
    },
    "articleEdit": {
        "title": "文章撰写",
        "new": "新文章",
        "inputTitle": "请输入标题（最多100个字）",
        "inputAlias": "请输入别名",
        "cover": "文章封面",
        "commentAble": "允许评论",
        "requireTitle": "文章标题不能为空",
        "requireType": "文章分类不能为空",
        "settings": "设置",
        "saving": "保存中",
        "saveFailed": "保存失败",
        "rollbackFailed": "回滚失败",
        "editExitWithoutSave": "文章未保存，无法离开页面",
        "editor": {
            "placeholder": "使用 ZrLog，开始愉快地写作吧"
        },
        "digest": {
            "label": "摘要",
            "tips": "一段好的摘要，能为你的读者提供一个非常好的引导"
        },
        "tag": {
            "label": "标签",
            "tips": "添加标签",
            "all": "全部"
        },
        "status": {
            "private": "私密",
            "draft": "草稿",
            "published": "公开"
        },
        "actions": {
            "save": "保存",
            "release": "发布",
            "saveAsDraft": "存为草稿"
        },
        "upload": {
            "tips": "拖拽上传或点击上传"
        },
        "version": {
            "label": "版本历史",
            "fields": {
                "title": "标题",
                "markdown": "内容"
            },
            "rollback": {
                "tip": "确定要回滚到该版本吗？当前内容会被历史版本覆盖。",
                "label": "回滚"
            },
            "current": "当前版本",
            "compare": "对比版本",
            "empty": "暂无历史版本",
            "select": "选择一个历史版本进行对比",
            "compareAction": "对比"
        }
    },
    "articleType": {
        "title": "分类",
        "size": "文章数量"
    },
    "nav": {
        "title": "导航",
        "name": "导航名称",
        "link": "链接"
    },
    "link": {
        "title": "链接",
        "name": "链接名称"
    },
    "comment": {
        "title": "评论",
        "content": "内容",
        "date": "评论时间",
        "userHome": "评论者主页",
        "nickName": "昵称"
    },
    "plugin": {
        "title": "插件"
    },
    "website": {
        "title": "基础设置",
        "summary": "用于维护站点名称、简介、作者与 favicon，这些信息会直接影响站点的基础展示与品牌识别。",
        "description": "网站简介",
        "descriptionTip": "常用于搜索引擎摘要和页面介绍，建议简明概括站点内容。",
        "keywordsTip": "多个关键词建议使用英文逗号分隔，主要用于站点元信息。",
        "faviconTip": "浏览器标签页、收藏夹和部分设备快捷入口会使用这个图标，建议上传清晰的小尺寸方形图标。"
    },
    "websiteBlog": {
        "title": "博客设置",
        "summary": "用于调整博客访问地址、静态化开关、评论状态与系统通知，影响站点的运行方式与访客体验。",
        "host": "主页地址",
        "hostTip": "建议填写站点对外访问的完整地址。启用静态化时该项不能为空，否则无法保存。",
        "staticSite": "静态化站点",
        "staticSiteTip": "开启后会生成静态页面，用于提升访问性能。通常需要配合正确的主页地址使用。",
        "disableComment": "关闭评论",
        "disableCommentTip": "开启后，前台将不再允许访客发表评论。",
        "systemNotify": "系统通知",
        "systemNotifyTip": "显示在前台的系统公告或提示信息，适合填写简短通知内容。",
        "cover": "文章封面"
    },
    "websiteAdmin": {
        "title": "管理设置",
        "summary": "用于设置后台语言、界面主题、会话时长等管理项，主要影响后台的使用习惯与操作体验。",
        "theme": {
            "label": "主题",
            "option": {
                "default": "默认",
                "antd": "Ant Design",
                "bootstrap": "Bootstrap",
                "geek": "极客",
                "cartoon": "卡通",
                "glass": "玻璃",
                "shadcn": "Shadcn",
                "illustration": "插画"
            }
        },
        "dark": {
            "mode": "暗黑模式"
        },
        "compact": {
            "mode": "紧凑模式"
        },
        "color": {
            "primary": "主题主色",
            "namedPresets": "命名预设",
            "preset": {
                "azureBlue": "拂晓蓝",
                "skyBlue": "冰川蓝",
                "deepBlue": "深海蓝",
                "indigoBlue": "星夜蓝",
                "violetPurple": "鸢尾紫",
                "deepPurple": "暮光紫",
                "midPurple": "葡萄紫",
                "magentaPink": "落樱粉",
                "fuchsiaPink": "玫影粉",
                "cyanTeal": "海湾青",
                "iceBlue": "霜空蓝",
                "deepTeal": "翡翠青",
                "green": "森林绿",
                "lightGreen": "薄荷绿",
                "yellowGreen": "春芽绿",
                "lime": "电光绿",
                "brightYellow": "日曜黄",
                "lemonYellow": "柠光黄",
                "goldenYellow": "香槟金",
                "mustardYellow": "琥珀金",
                "orange": "熔岩橙",
                "amberOrange": "琥珀橙",
                "orangeRed": "霞焰红",
                "burntOrange": "枫叶橙",
                "red": "烈焰红",
                "tomatoRed": "珊瑚红",
                "brown": "胡桃棕",
                "slateBlueGray": "星岩灰",
                "charcoalGray": "曜石黑"
            }
        },
        "staticResource": {
            "url": "管理页静态资源（URL）",
            "urlTips": "留空即禁用",
            "urlHelp": "可填写 http:// 或 https:// 开头的静态资源根地址。配置错误会导致后台样式或脚本加载失败。"
        },
        "session": {
            "timeout": "后台会话超时",
            "timeoutHelp": "单位为分钟，需大于 5。超过该时长未操作后，当前登录会话将失效。",
            "timeoutUnit": "分钟"
        },
        "language": {
            "label": "语言",
            "chinese": "简体中文",
            "english": "English"
        },
        "moreSettings": "文章与编辑器设置",
        "article": {
            "pageSize": "后台文章每页数量",
            "pageSizeTip": "控制后台文章列表每页显示的数量，只影响管理界面的分页显示。",
            "autoDigestLengthTips": "文章自动摘要最大长度",
            "autoDigestLengthHelp": "建议填写正整数。留空或填写非正数时，将回退为系统默认摘要长度。"
        },
        "pwa": {
            "icon192Help": "用于安装到主屏幕时的应用图标，建议上传清晰的正方形图片。",
            "icon512Help": "用于高分辨率设备或启动画面相关场景，建议上传清晰的 512 x 512 正方形图片。"
        }
    },
    "websiteTemplate": {
        "title": "主题设置",
        "summary": "用于切换和预览当前主题，并管理主题相关资源，主要决定站点前台的视觉风格与布局表现。"
    },
    "websiteOther": {
        "title": "其他设置",
        "summary": "用于维护备案信息、统计脚本与 robots.txt 等补充配置，影响搜索收录、统计接入与合规展示。",
        "statistics": "网站统计代码",
        "statisticsTip": "这里的内容会原样注入前台页面，请仅粘贴可信的统计脚本代码。",
        "icp": "ICP备案信息",
        "icpTip": "通常显示在前台页脚，可填写备案号或带链接的 HTML 内容。",
        "robots": "robots.txt",
        "robotsTip": "用于控制搜索引擎抓取规则。写错可能影响页面收录或导致敏感路径被暴露。",
        "security": {
            "xssTips": "此输入区域支持 HTML 内容，HTML 中的 JavaScript 也将被执行，请确保输入内容安全"
        }
    },
    "websiteAi": {
        "title": "AI 助手设置",
        "label": "AI 助手",
        "summary": "用于配置 AI 服务提供方、模型、密钥与提示词，决定后台可调用的智能能力及其输出方式。",
        "aiApiKey": "API KEY",
        "aiApiKeyTip": "用于服务端请求 AI 提供方接口，请填写与所选提供方匹配的有效密钥。",
        "aiProvider": "提供方",
        "aiModel": "模型",
        "aiPrompt": "提示词",
        "aiPromptTip": "作为默认提示词参与生成，会影响输出风格、语气和约束条件。"
    },
    "websiteUpgrade": {
        "title": "更新设置",
        "summary": "用于设置自动升级与预览策略，关系到版本更新的节奏、方式，以及升级前的风险控制。",
        "autoCheckCycle": "自动检查周期",
        "autoCheckCycleTip": "仅用于定时检查新版本，不会自动执行升级。",
        "cycle": {
            "oneDay": "一天",
            "oneWeek": "一周",
            "halfMonth": "半个月",
            "never": "从不"
        },
        "canPreview": "预览版本检查",
        "canPreviewTip": "开启后，检查更新时会包含预览版，适合愿意提前体验新功能的场景。"
    },
    "websiteVersion": {
        "title": "版本信息"
    },
    "templateCenter": {
        "title": "主题中心",
        "download": "下载"
    },
    "templateConfig": {
        "inUse": "使用中",
        "inPreview": "预览中"
    },
    "user": {
        "title": "个人信息",
        "logout": "退出",
        "userName": "用户名",
        "email": "邮箱",
        "headPortrait": "头像"
    },
    "accountSecurity": {
        "title": "账户安全",
        "passwordTitle": "更改密码",
        "oldPassword": "旧密码",
        "newPassword": "新密码",
        "mfaTitle": "多重验证",
        "mfaEnabled": "已开启多重验证，登录时需要额外输入验证码。",
        "mfaDisabled": "当前未开启多重验证，建议启用以提升账号安全性。",
        "mfaSecret": "验证密钥",
        "mfaCode": "验证码",
        "mfaCodePlaceholder": "输入 6 位验证码",
        "mfaSetupHint": "请使用身份验证器扫描二维码，再输入当前 6 位验证码完成启用。",
        "mfaSetupUrl": "配置链接",
        "enableMfa": "启用多重验证",
        "disableMfa": "关闭多重验证"
    },
    "system": {
        "info": "系统信息",
        "runtimeEnvironment": "运行环境",
        "resourceOverview": "资源概览",
        "health": {
            "title": "系统健康体检",
            "refresh": "重新检测",
            "optimize": "一键优化",
            "optimizing": "优化中",
            "lastChecked": "最近检测",
            "score": "健康评分",
            "brokenLinks": "死链",
            "seoMissing": "SEO 缺失",
            "databaseFragment": "数据库碎片",
            "database": "数据库",
            "issues": "问题项",
            "suggestions": "优化建议",
            "empty": "当前没有发现明显问题",
            "optimizeSuccess": "数据库优化已执行，体检结果已刷新",
            "issueAction": "前往处理",
            "suggestionAction": "前往设置",
            "issueMeta": {
                "brokenLinks": {
                    "title": "存在本地资源死链",
                    "detail": "部分文章引用了已经不存在的本地文件或附件。"
                },
                "seoMissing": {
                    "title": "SEO 元信息不完整",
                    "detail": "站点基础信息或已发布文章缺少摘要、关键字等元信息。"
                },
                "databaseFragment": {
                    "title": "建议执行数据库维护",
                    "detail": "当前数据库存在可回收空间或统计信息维护需求。"
                },
                "directoryWritable": {
                    "title": "目录写入权限异常",
                    "detail": "缓存目录或静态目录无法正常创建、写入或删除文件，可能影响上传、缓存和静态化。"
                }
            },
            "suggestionMeta": {
                "repairBrokenLinks": {
                    "title": "修复本地资源死链",
                    "detail": "检查相关文章，更新失效引用，或重新上传缺失附件。"
                },
                "completeWebsiteSeo": {
                    "title": "补全站点 SEO 字段",
                    "detail": "到网站设置中补齐站点标题、简介和关键字。"
                },
                "completeArticleSeo": {
                    "title": "补全文章 SEO 字段",
                    "detail": "为已发布文章补齐摘要和关键字，提高搜索摘要质量。"
                },
                "databaseOptimize": {
                    "title": "执行数据库维护",
                    "detail": "使用一键优化回收空间或刷新数据库统计信息。"
                },
                "repairDirectoryWritable": {
                    "title": "检查目录写入权限",
                    "detail": "确认 cache 和 static 目录存在，并为当前运行用户授予创建、写入和删除文件的权限。"
                },
                "healthy": {
                    "title": "当前未发现明显问题",
                    "detail": "建议在批量导入内容、切换主题或清理附件后再次运行体检。"
                }
            }
        }
    },
    "upgrade": {
        "wizard": "更新向导",
        "check": "检查新版本",
        "doUpgrade": "去更新",
        "changeLog": "变更日志",
        "nextStep": "下一步",
        "detectedPrefix": "发现",
        "execute": "执行更新",
        "download": "下载更新",
        "downloadingPackage": "下载更新包",
        "executing": "正在执行更新..."
    },
    "login": {
        "title": "登录",
        "userNameAndPassword": "请输入用户名和密码",
        "userName": "用户名",
        "password": "密码",
        "mfaCode": "验证码",
        "mfaCodePlaceholder": "如已开启多重验证，请输入 6 位验证码",
        "mfaStepHint": "用户名和密码已验证，请输入身份验证器中的 6 位验证码继续登录",
        "mfaSubmit": "继续登录",
        "mfaBack": "返回上一步",
        "backendServerUrl": "网关 URL",
        "submit": "登录",
        "copyrightCurrentYear": "Copyright © 2026"
    },
    "submit": "提交",
    "preview": "预览",
    "introduction": "简介",
    "notFound": "未找到",
    "title": "标题",
    "subTitle": "子标题",
    "keywords": "关键字",
    "author": "作者",
    "type": "分类",
    "edit": "编辑",
    "alias": "别名",
    "actions": "操作",
    "order": "排序",
    "yes": "是",
    "no": "否",
    "deleteTips": "确定删除吗？",
    "close": "关闭",
    "pleaseChoose": "请选择",
    "add": "添加",
    "favicon": "网站图标",
    "id": "编号",
    "icon": "图标",
    "recent": "最近使用",
    "confirm": "确认",
    "cancel": "取消",
    "copyright": "Copyright © 2013-2026",
    "common": {
        "preset": "预设",
        "tips": "提示",
        "close": "关闭",
        "settings": "设置",
        "management": "后台管理"
    },
    "fullscreen": {
        "enter": "进入全屏",
        "exit": "退出全屏"
    },
    "error": {
        "unknown": "未知错误",
        "serviceException": "服务异常",
        "requestError": "请求错误",
        "networkOffline": "网络已离线"
    },
    "staticSite": {
        "syncFailed": "同步失败",
        "syncComplete": "同步完成",
        "syncIncomplete": "同步未完成",
        "publishStart": "正在发布文章",
        "generatingHtml": "正在生成静态页面",
        "generatingHtmlBlog": "正在生成博客静态页面",
        "generatingHtmlAdmin": "正在生成后台静态页面",
        "generatingHtmlAll": "正在生成后台和博客静态页面",
        "syncing": "正在同步静态页面",
        "syncingBlog": "正在同步博客静态页面",
        "syncingAdmin": "正在同步后台静态页面",
        "syncingAll": "正在同步后台和博客静态页面",
        "publishComplete": "发布完成",
        "retrying": "重试"
    },
    "backgroundTask": {
        "entry": "消息中心",
        "title": "消息中心",
        "leaveHint": "这里会集中显示系统通知、版本更新和任务进度，方便你随时查看最近信息。",
        "emptyTitle": "暂时没有消息",
        "emptyDetail": "系统通知、版本更新和任务进度会集中出现在这里。",
        "clearFinished": "清除已完成",
        "started": "任务已开始",
        "finished": "任务已完成",
        "updatedAt": "最近更新",
        "versionUpdate": {
            "title": "发现新版本",
            "current": "可升级到",
            "action": "前往升级",
            "publishedAt": "发布时间"
        },
        "unreadComment": {
            "title": "收到新评论",
            "pending": "当前有 {count} 条新评论待处理。",
            "action": "前往查看"
        },
        "status": {
            "running": "进行中",
            "success": "已完成",
            "error": "失败",
            "notice": "通知"
        }
    },
    "offline": {
        "short": "已离线"
    }
} as const;

type WidenI18n<T> = {
    [K in keyof T]: T[K] extends string ? string : WidenI18n<T[K]>;
};

export type AdminI18nResource = WidenI18n<typeof zhCN>;

const enUS = {
    "goBack": "Back",
    "key": "Name",
    "value": "Value",
    "searchTip": "Enter keywords...",
    "globalSearchTip": "Global Search",
    "index": {
        "title": "Dashboard",
        "welcome": {
            "systemInfo": "System Info",
            "viewSystem": "View System Info",
            "currentVersion": "Current Version"
        },
        "statistics": "Statistics Overview",
        "status": "Content Status",
        "storage": {
            "disk": "Disk Usage",
            "cache": "Cache Usage"
        },
        "audit": {
            "label": "Recent Operations",
            "more": "View more",
            "full": "Full Operation Audit (Latest 100)",
            "empty": "No audit logs yet",
            "crawler": "Crawler"
        },
        "insight": {
            "empty": {
                "category": "No category data yet",
                "tag": "No tag data yet"
            },
            "label": "Content Insights",
            "categoryDistribution": "Category Distribution",
            "hotTags": "Hot Tags"
        },
        "activity": "Activity",
        "activityGraph": {
            "month": {
                "jan": "Jan",
                "feb": "Feb",
                "mar": "Mar",
                "apr": "Apr",
                "may": "May",
                "jun": "Jun",
                "jul": "Jul",
                "aug": "Aug",
                "sep": "Sep",
                "oct": "Oct",
                "nov": "Nov",
                "dec": "Dec"
            },
            "weekday": {
                "mon": "Mon",
                "wed": "Wed",
                "fri": "Fri"
            },
            "noData": "No data"
        },
        "statisticsCard": {
            "todayComment": "Comments Today",
            "totalComment": "Total Comments",
            "totalArticle": "Total Articles",
            "totalArticleView": "Total Views"
        },
        "quickAction": {
            "label": "Quick Actions",
            "writeArticle": "Write an Article",
            "backupFiles": "Backup Files",
            "localDraft": "Unfinished Draft",
            "localDraftTag": "Local Cache",
            "localDraftUntitled": "Untitled Draft",
            "localDraftTip": "An unfinished local draft was found. You can jump back into the editor and continue.",
            "continueWriting": "Continue Writing",
            "clearLocalDraft": "Clear Local Draft",
            "clearLocalDraftConfirm": "This local draft cannot be recovered after clearing. Continue?"
        }
    },
    "article": {
        "title": "Article Management",
        "label": "Article",
        "commentAble": "Allow comments",
        "search": "Search",
        "status": {
            "private": "Private",
            "draft": "Draft",
            "published": "Public"
        },
        "tag": {
            "all": "All"
        },
        "previewOpenPrefix": "Click to view \"",
        "previewOpenSuffix": "\"",
        "createTime": "Create Time",
        "lastUpdateDate": "Last Updated",
        "viewCount": "Views",
        "commentSize": "Comments",
        "listSearchTip": "Search Articles",
        "cover":"Cover",
    },
    "articleEdit": {
        "title": "Article Writing",
        "new": "New Article",
        "inputTitle": "Enter a title (up to 100 characters)",
        "inputAlias": "Enter an alias",
        "cover": "Article Cover",
        "commentAble": "Allow comments",
        "requireTitle": "Article title is required",
        "requireType": "Article category is required",
        "settings": "Settings",
        "saving": "Saving",
        "saveFailed": "Save failed",
        "rollbackFailed": "Rollback failed",
        "editExitWithoutSave": "The article has not been saved. You cannot leave this page.",
        "editor": {
            "placeholder": "Start writing with ZrLog"
        },
        "digest": {
            "label": "Summary",
            "tips": "A good summary gives readers a clear introduction"
        },
        "tag": {
            "label": "Tag",
            "tips": "Add a tag",
            "all": "All"
        },
        "status": {
            "private": "Private",
            "draft": "Draft",
            "published": "Public"
        },
        "actions": {
            "save": "Save",
            "release": "Publish",
            "saveAsDraft": "Save as Draft"
        },
        "upload": {
            "tips": "Drag a file here or click to upload"
        },
        "version": {
            "label": "Version History",
            "fields": {
                "title": "Title",
                "markdown": "Content"
            },
            "rollback": {
                "tip": "Rollback to this version? Current content will be overwritten.",
                "label": "Rollback"
            },
            "current": "Current Version",
            "compare": "Comparison Version",
            "empty": "No version history",
            "select": "Select a historical version to compare",
            "compareAction": "Compare"
        }
    },
    "articleType": {
        "title": "Categories",
        "size": "Article Count"
    },
    "nav": {
        "title": "Navigation",
        "name": "Navigation Name",
        "link": "Link"
    },
    "link": {
        "title": "Links",
        "name": "Link Name"
    },
    "comment": {
        "title": "Comments",
        "content": "Content",
        "date": "Comment Time",
        "userHome": "Commenter's Website",
        "nickName": "Nickname"
    },
    "plugin": {
        "title": "Plugins"
    },
    "website": {
        "title": "Basic Settings",
        "summary": "Maintain the site name, description, author, and favicon. These settings directly affect the site's basic presentation and brand recognition.",
        "description": "Website Description",
        "descriptionTip": "Often used for search snippets and page introductions. Keep it concise and descriptive.",
        "keywordsTip": "If you use multiple keywords, separate them with commas. These are mainly used as page metadata.",
        "faviconTip": "Used in browser tabs, bookmarks, and some device shortcuts. Upload a clear small square icon."
    },
    "websiteBlog": {
        "title": "Blog Settings",
        "summary": "Adjust the blog URL, static generation, comment status, and system notifications. These settings affect how the site runs and how visitors experience it.",
        "host": "Homepage URL",
        "hostTip": "Use the full public URL of the site. When static generation is enabled, this field must not be empty.",
        "staticSite": "Static Site Generation",
        "staticSiteTip": "Generates static pages to improve delivery performance. Usually this should be paired with a correct homepage URL.",
        "disableComment": "Disable Comments",
        "disableCommentTip": "When enabled, visitors can no longer submit comments on the public site.",
        "systemNotify": "System Notifications",
        "systemNotifyTip": "A short announcement or notice shown on the public site.",
        "cover": "Article Cover"
    },
    "websiteAdmin": {
        "title": "Admin Settings",
        "summary": "Configure the admin language, interface theme, session duration, and related management options. These settings mainly shape the admin workflow and operating experience.",
        "theme": {
            "label": "Theme",
            "option": {
                "default": "Default",
                "antd": "Ant Design",
                "bootstrap": "Bootstrap",
                "geek": "Geek",
                "cartoon": "Cartoon",
                "glass": "Glass",
                "shadcn": "Shadcn",
                "illustration": "Illustration"
            }
        },
        "dark": {
            "mode": "Dark Mode"
        },
        "compact": {
            "mode": "Compact Mode"
        },
        "color": {
            "primary": "Primary Theme Color",
            "namedPresets": "Named Presets",
            "preset": {
                "azureBlue": "Dawn Blue",
                "skyBlue": "Glacier Blue",
                "deepBlue": "Deep Sea Blue",
                "indigoBlue": "Starlit Blue",
                "violetPurple": "Iris Violet",
                "deepPurple": "Twilight Purple",
                "midPurple": "Grape Purple",
                "magentaPink": "Sakura Pink",
                "fuchsiaPink": "Rose Glow",
                "cyanTeal": "Lagoon Teal",
                "iceBlue": "Frost Blue",
                "deepTeal": "Emerald Teal",
                "green": "Forest Green",
                "lightGreen": "Mint Green",
                "yellowGreen": "Spring Bud",
                "lime": "Neon Lime",
                "brightYellow": "Sunburst Yellow",
                "lemonYellow": "Lemon Glow",
                "goldenYellow": "Champagne Gold",
                "mustardYellow": "Amber Gold",
                "orange": "Lava Orange",
                "amberOrange": "Amber Orange",
                "orangeRed": "Sunset Ember",
                "burntOrange": "Maple Orange",
                "red": "Flame Red",
                "tomatoRed": "Coral Red",
                "brown": "Walnut Brown",
                "slateBlueGray": "Meteor Gray",
                "charcoalGray": "Obsidian Black"
            }
        },
        "staticResource": {
            "url": "Admin Static Assets URL",
            "urlTips": "Leave empty to disable",
            "urlHelp": "Enter an http:// or https:// static asset base URL. A bad value can cause admin styles or scripts to fail to load."
        },
        "session": {
            "timeout": "Admin Session Timeout",
            "timeoutHelp": "Measured in minutes and must be greater than 5. After this idle period, the current admin session expires.",
            "timeoutUnit": "Minutes"
        },
        "language": {
            "label": "Language",
            "chinese": "简体中文",
            "english": "English"
        },
        "moreSettings": "Article and Editor Settings",
        "article": {
            "pageSize": "Articles Per Page in Admin",
            "pageSizeTip": "Controls how many articles are shown per page in the admin list. It only affects admin-side pagination.",
            "autoDigestLengthTips": "Maximum Auto Summary Length",
            "autoDigestLengthHelp": "Use a positive integer. If left empty or set to a non-positive value, the system falls back to the default summary length."
        },
        "pwa": {
            "icon192Help": "Used as the app icon when the site is added to the home screen. Upload a clear square image.",
            "icon512Help": "Used for high-resolution devices and splash-related scenarios. Upload a clear 512 x 512 square image."
        }
    },
    "websiteTemplate": {
        "title": "Theme Settings",
        "summary": "Switch and preview the current theme, and manage theme-related resources. These settings largely determine the site's visual style and layout."
    },
    "websiteOther": {
        "title": "Other Settings",
        "summary": "Maintain ICP filing information, analytics scripts, and robots.txt settings. These options affect search indexing, analytics integration, and compliance display.",
        "statistics": "Analytics Code",
        "statisticsTip": "This content is injected into the public site as-is. Only paste trusted analytics scripts.",
        "icp": "ICP Filing",
        "icpTip": "Usually displayed in the site footer. You can enter a filing number or HTML content with a link.",
        "robots": "robots.txt",
        "robotsTip": "Controls crawler rules for your site. A bad rule can affect indexing or expose paths you did not intend to allow.",
        "security": {
            "xssTips": "This input area supports HTML. JavaScript in HTML will also execute; make sure the content is safe."
        }
    },
    "websiteAi": {
        "title": "AI Assistant Settings",
        "label": "AI Assistant",
        "summary": "Configure the AI provider, model, API key, and prompt settings. These options determine the AI capabilities available in the admin area and how they respond.",
        "aiApiKey": "API KEY",
        "aiApiKeyTip": "Used by the server to call the selected AI provider. Enter a valid key that matches the chosen provider.",
        "aiProvider": "Provider",
        "aiModel": "Model",
        "aiPrompt": "Prompt",
        "aiPromptTip": "Acts as the default prompt context and influences output style, tone, and constraints."
    },
    "websiteUpgrade": {
        "title": "Update Settings",
        "summary": "Configure automatic update checks and preview strategies. These settings affect update cadence, update method, and pre-update risk control.",
        "autoCheckCycle": "Auto Check Interval",
        "autoCheckCycleTip": "This only schedules update checks. It does not perform upgrades automatically.",
        "cycle": {
            "oneDay": "One Day",
            "oneWeek": "One Week",
            "halfMonth": "Half a Month",
            "never": "Never"
        },
        "canPreview": "Preview Version Check",
        "canPreviewTip": "When enabled, update checks include preview builds. Use this if you want early access to upcoming changes."
    },
    "websiteVersion": {
        "title": "Version Info"
    },
    "templateCenter": {
        "title": "Theme Center",
        "download": "Download"
    },
    "templateConfig": {
        "inUse": "In Use",
        "inPreview": "In Preview"
    },
    "user": {
        "title": "Personal Information",
        "logout": "Sign out",
        "userName": "Username",
        "email": "Email",
        "headPortrait": "Avatar"
    },
    "accountSecurity": {
        "title": "Account Security",
        "passwordTitle": "Change Password",
        "oldPassword": "Old Password",
        "newPassword": "New Password",
        "mfaTitle": "Multi-factor Authentication",
        "mfaEnabled": "Multi-factor authentication is enabled. A verification code is required when signing in.",
        "mfaDisabled": "Multi-factor authentication is not enabled. Turn it on to better protect this account.",
        "mfaSecret": "Verification Secret",
        "mfaCode": "Verification Code",
        "mfaCodePlaceholder": "Enter the 6-digit code",
        "mfaSetupHint": "Scan the QR code with your authenticator app, then enter the current 6-digit code to enable it.",
        "mfaSetupUrl": "Setup URL",
        "enableMfa": "Enable Multi-factor Authentication",
        "disableMfa": "Disable Multi-factor Authentication"
    },
    "system": {
        "info": "System Info",
        "runtimeEnvironment": "Runtime Environment",
        "resourceOverview": "Resource Overview",
        "health": {
            "title": "Health Check",
            "refresh": "Refresh",
            "optimize": "Optimize",
            "optimizing": "Optimizing",
            "lastChecked": "Last checked",
            "score": "Health score",
            "brokenLinks": "Broken links",
            "seoMissing": "Missing SEO",
            "databaseFragment": "Database fragmentation",
            "database": "Database",
            "issues": "Issues",
            "suggestions": "Suggestions",
            "empty": "No obvious issues were found",
            "optimizeSuccess": "Database optimization completed and the health report was refreshed",
            "issueAction": "Open",
            "suggestionAction": "Open settings",
            "issueMeta": {
                "brokenLinks": {
                    "title": "Broken local resources detected",
                    "detail": "Some articles reference local files or attachments that no longer exist."
                },
                "seoMissing": {
                    "title": "SEO metadata is incomplete",
                    "detail": "Site settings or published articles are missing summary and keyword metadata."
                },
                "databaseFragment": {
                    "title": "Database maintenance is recommended",
                    "detail": "The current database has reclaimable space or needs statistics maintenance."
                },
                "directoryWritable": {
                    "title": "Directory write permission issue",
                    "detail": "The cache or static directory cannot create, write, or delete files, which may affect uploads, caching, and static generation."
                }
            },
            "suggestionMeta": {
                "repairBrokenLinks": {
                    "title": "Repair broken local resources",
                    "detail": "Review the affected articles, update invalid references, or re-upload missing attachments."
                },
                "completeWebsiteSeo": {
                    "title": "Complete site SEO fields",
                    "detail": "Fill in the site title, description, and keywords in website settings."
                },
                "completeArticleSeo": {
                    "title": "Complete article SEO fields",
                    "detail": "Add digest and keywords to published articles to improve search snippets."
                },
                "databaseOptimize": {
                    "title": "Run database maintenance",
                    "detail": "Use one-click optimization to reclaim space or refresh database statistics."
                },
                "repairDirectoryWritable": {
                    "title": "Check directory write permissions",
                    "detail": "Ensure the cache and static directories exist and that the current runtime user can create, write, and delete files there."
                },
                "healthy": {
                    "title": "No obvious issues detected",
                    "detail": "Run the health check again after large content imports, theme changes, or attachment cleanup."
                }
            }
        }
    },
    "upgrade": {
        "wizard": "Upgrade Wizard",
        "check": "Check for Updates",
        "doUpgrade": "Update",
        "changeLog": "Changelog",
        "nextStep": "Next step",
        "detectedPrefix": "New ",
        "execute": "Execute Update",
        "download": "Download Update",
        "downloadingPackage": "Download Update Package",
        "executing": "Executing update..."
    },
    "login": {
        "title": "Sign in",
        "userNameAndPassword": "Please enter your username and password",
        "userName": "Username",
        "password": "Password",
        "mfaCode": "Verification Code",
        "mfaCodePlaceholder": "Enter the 6-digit code if multi-factor authentication is enabled",
        "mfaStepHint": "Your username and password are verified. Enter the 6-digit code from your authenticator app to continue.",
        "mfaSubmit": "Continue",
        "mfaBack": "Back",
        "backendServerUrl": "Gateway URL",
        "submit": "Sign in",
        "copyrightCurrentYear": "Copyright © 2026"
    },
    "submit": "Submit",
    "preview": "Preview",
    "introduction": "Introduction",
    "notFound": "Not found",
    "title": "Title",
    "subTitle": "Subtitle",
    "keywords": "Keywords",
    "author": "Author",
    "type": "Type",
    "edit": "Edit",
    "alias": "Alias",
    "actions": "Actions",
    "order": "Order",
    "yes": "Yes",
    "no": "No",
    "deleteTips": "Are you sure you want to delete?",
    "close": "Close",
    "pleaseChoose": "Please choose",
    "add": "Add",
    "favicon": "Favicon",
    "id": "ID",
    "icon": "Icon",
    "recent": "Recent",
    "confirm": "Confirm",
    "cancel": "Cancel",
    "copyright": "Copyright © 2013-2026",
    "common": {
        "preset": "Preset",
        "tips": "Notice",
        "close": "Close",
        "settings": "Settings",
        "management": "Admin Console"
    },
    "fullscreen": {
        "enter": "Enter full screen",
        "exit": "Exit full screen"
    },
    "error": {
        "unknown": "Unknown error",
        "serviceException": "Service exception",
        "requestError": "Request error",
        "networkOffline": "Network offline"
    },
    "staticSite": {
        "syncFailed": "Sync failed",
        "syncComplete": "Sync completed",
        "syncIncomplete": "Sync not completed",
        "publishStart": "Publishing article",
        "generatingHtml": "Generating static pages",
        "generatingHtmlBlog": "Generating blog static pages",
        "generatingHtmlAdmin": "Generating admin static pages",
        "generatingHtmlAll": "Generating admin and blog static pages",
        "syncing": "Syncing static pages",
        "syncingBlog": "Syncing blog static pages",
        "syncingAdmin": "Syncing admin static pages",
        "syncingAll": "Syncing admin and blog static pages",
        "publishComplete": "Publish completed",
        "retrying": "Retrying"
    },
    "backgroundTask": {
        "entry": "Messages",
        "title": "Message Center",
        "leaveHint": "System notices, version updates, and task progress appear here so you can check recent information at any time.",
        "emptyTitle": "No messages right now",
        "emptyDetail": "System notices, version updates, and task progress will appear here.",
        "clearFinished": "Clear finished",
        "started": "Task started",
        "finished": "Task completed",
        "updatedAt": "Updated",
        "versionUpdate": {
            "title": "New version available",
            "current": "Upgrade to",
            "action": "Open upgrade",
            "publishedAt": "Published"
        },
        "unreadComment": {
            "title": "New comments received",
            "pending": "{count} new comments are waiting for review.",
            "action": "Open comments"
        },
        "status": {
            "running": "Running",
            "success": "Completed",
            "error": "Failed",
            "notice": "Notice"
        }
    },
    "offline": {
        "short": "Offline"
    }
} satisfies AdminI18nResource;

const adminI18nResources: Record<AdminLang, AdminI18nResource> = {
    zh_CN: zhCN,
    en_US: enUS,
};

const normalizeLang = (lang?: string): AdminLang => {
    return lang === "en_US" ? "en_US" : "zh_CN";
};

export const getAdminI18n = (lang?: string): AdminI18nResource => {
    return adminI18nResources[normalizeLang(lang)];
};
