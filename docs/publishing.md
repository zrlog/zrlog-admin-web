# 发布认证

`main` 通过 `-PnodeBuild,snapshot clean deploy` 发布开发快照，`v*` tag 通过 Central Publishing 发布正式版。认证信息由 GitHub Actions Secrets 注入。

Test Coverage 工作流在生成前端资源和执行测试后，使用发布同款 JDK 11 执行 `./mvnw -B -ntp -DskipTests package javadoc:jar`，为全部 Java 模块生成 Javadoc JAR。此步骤不启用 `snapshot` profile，不使用发布凭证，也不上传产物；Javadoc 的错误会使 PR 检查失败。不要通过关闭 doclint、忽略错误或跳过 Javadoc 绕过发布校验。

Maven 3.10 起会按仓库 origin 校验凭证；`central` 默认关联的下载域名与 Sonatype 发布域名不同。发布工作流读取实际 Maven 版本，3.10 及以上使用 settings 1.3.0，并为 `central` 显式声明 `https://central.sonatype.com`；旧 Maven 保留 settings 1.0.0，避免不支持 `repositoryOrigins` 的警告。升级 Maven 时须验证 HTTP 认证上传，文件仓库部署无法覆盖凭证校验。
