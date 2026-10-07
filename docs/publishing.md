# 发布认证

`main` 通过 `-PnodeBuild,snapshot clean deploy` 发布开发快照，`v*` tag 通过 Central Publishing 发布正式版。认证信息由 GitHub Actions Secrets 注入。

Maven 3.10 起会按仓库 origin 校验凭证；`central` 默认关联的下载域名与 Sonatype 发布域名不同。发布工作流读取实际 Maven 版本，3.10 及以上使用 settings 1.3.0，并为 `central` 显式声明 `https://central.sonatype.com`；旧 Maven 保留 settings 1.0.0，避免不支持 `repositoryOrigins` 的警告。升级 Maven 时须验证 HTTP 认证上传，文件仓库部署无法覆盖凭证校验。
