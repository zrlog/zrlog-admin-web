# Native Image 规则

本工程在 native image 模式下依赖 `AdminNativeImageUtils` 预注册 Gson 需要访问的类型和资源。新增接口数据结构时，如果没有同步注册，运行到对应接口时可能出现序列化或反序列化失败。

## DTO 注册规则

- 后台管理端新增 `request`、`response`、`VO`、嵌套类等，只要会被 Gson 参与 JSON 序列化或反序列化，就要同步更新 所属模块 `src/main/java/com/zrlog/admin/util/*NativeImageUtils.java`。
- 请求体相关类型注册到所属模块的 `reg()`。
- 响应体相关类型注册到所属模块的 `reg()`。
- 如果 response 内部包含新的嵌套对象、列表元素类型或静态内部类，也要一并注册，不要只注册最外层类。
- 新增接口时，不要假设 `import com.zrlog.admin.business.rest.response.*;` 已经足够；是否能在 native image 下工作，取决于是否加入了 `NativeImageUtils.gsonNativeAgentByClazz(...)` 白名单。

## 资源注册规则

- 如果新增 native image 启动时必须读取的静态资源、配置文件或 i18n 资源，也要同步更新 `getResources(...)` 的资源列表。

## Controller 反射规则

各模块在 `src/main/resources/META-INF/native-image/com.hibegin/<模块名>/reflect-config.json` 显式注册 Controller 构造器及路由方法；有请求/响应构造器时也要注册，框架会优先使用它。新增或迁移路由时同步维护所属模块的配置；页面路由只在 UI 模块注册。`AdminNativeImageUtilsTest` 按实际组装后的路由检查覆盖，避免漏配。

不能只依赖 agent 请求采样：未安装数据库时，Controller 的服务字段初始化可能在方法调用前失败，此时采样只记录构造器，Native 运行后会缺少接口方法的反射调用权限。

## 提交前检查

涉及管理端接口结构调整时，提交前至少检查：

```shell
rg -n "class .*Response|class .*Request|class .*VO" zrlog-admin-*/src/main/java/com/zrlog/admin/business
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
```

功能模块维护自己的显式 DTO 白名单；API 的 `AdminNativeImageUtils` 聚合后端类型与资源，UI 的 `AdminUiNativeImageUtils` 注册页面、PWA 和静态资源。构建 Native 时仍注册全部已打包功能，运行时禁用不会把类型从 Native 二进制裁掉。AI prompt 和模型目录列表由 `AiNativeImageUtils.resources()` 维护；WebAuthn 反射配置随 account 资源打包。首期不支持运行时移除功能 JAR。
