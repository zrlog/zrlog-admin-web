package com.zrlog.admin.web.plugin;

import com.hibegin.common.util.EnvKit;
import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public class FaasUpdateVersionHandler implements UpdateVersionHandler {

    private final Version version;
    private final Map<String, Object> backend;

    public FaasUpdateVersionHandler(Map<String, Object> backend, Version version) {
        this.version = version;
        this.backend = backend;
    }

    private static String getS3UpdateShell(String downloadUrl, String functionName) {
        String finalName = "zrlog-" + BlogBuildInfoUtil.getFileArch() + "-latest.zip";
        String envKey = "LAMBDA_S3_REPO_NAME";
        String lambdaRepoName = Objects.requireNonNullElse(System.getenv(envKey), "zrlog-update-bucket");
        File localFile = new File("/tmp/" + finalName);
        return "export LAMBDA_S3_REPO_NAME=\"" + lambdaRepoName + "\"\n" +
                "export AWS_LAMBDA_FUNCTION_NAME=\"" + functionName + "\"\n" +
                "UA=\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36\"\n" +
                "wget --user-agent=\"${UA}\" -O \"" + localFile + "\" \"" + downloadUrl + "\"\n" +
                "aws s3 cp " + localFile + " s3://${LAMBDA_S3_REPO_NAME}/" + finalName + "\n" +
                "aws lambda update-function-code \\\n" +
                "  --function-name ${AWS_LAMBDA_FUNCTION_NAME} \\\n" +
                "  --s3-bucket ${LAMBDA_S3_REPO_NAME} \\\n" +
                "  --s3-key " + finalName + "\n\n";
    }

    @Override
    public String getMessage() {
        String downloadUrl = version.getZipDownloadUrl().replaceFirst(".zip", "-faas.zip");
        if (EnvKit.isLambda()) {
            return "#### " + backend.get("lambdaUpdateTitle") + "\n```bash\n" +
                    getS3UpdateShell(downloadUrl, System.getenv("AWS_LAMBDA_FUNCTION_NAME")) +
                    "```";
        }
        return "[download upgrade file](" + downloadUrl + ")";
    }

    public static void main(String[] args) {
        String s3UpdateShell = getS3UpdateShell("https://dl.zrlog.com/preview/zrlog-Linux-amd64-faas.zip", "faas-zrlog-com");
        System.out.println(s3UpdateShell);
    }

    @Override
    public boolean isFinish() {
        return false;
    }

    @Override
    public void doHandle() {

    }
}
