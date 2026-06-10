# Native Image 规则

本工程在 native image 模式下依赖 `AdminNativeImageUtils` 预注册 Gson 需要访问的类型和资源。新增接口数据结构时，如果没有同步注册，运行到对应接口时可能出现序列化或反序列化失败。

## DTO 注册规则

- 后台管理端新增 `request`、`response`、`VO`、嵌套类等，只要会被 Gson 参与 JSON 序列化或反序列化，就要同步更新 `src/main/java/com/zrlog/admin/util/AdminNativeImageUtils.java`。
- 请求体相关类型注册到 `adminRequestJson()`。
- 响应体相关类型注册到 `adminResponseJson()`。
- 如果 response 内部包含新的嵌套对象、列表元素类型或静态内部类，也要一并注册，不要只注册最外层类。
- 新增接口时，不要假设 `import com.zrlog.admin.business.rest.response.*;` 已经足够；是否能在 native image 下工作，取决于是否加入了 `NativeImageUtils.gsonNativeAgentByClazz(...)` 白名单。

## 资源注册规则

- 如果新增 native image 启动时必须读取的静态资源、配置文件或 i18n 资源，也要同步更新 `getResources(...)` 的资源列表。

## 提交前检查

涉及管理端接口结构调整时，提交前至少检查：

```shell
rg -n "class .*Response|class .*Request|class .*VO" src/main/java/com/zrlog/admin/business
sed -n '1,220p' src/main/java/com/zrlog/admin/util/AdminNativeImageUtils.java
mvn -q -DskipTests compile
```
