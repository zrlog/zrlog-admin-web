package com.zrlog.admin.business.rest.base;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

public class OtherWebSiteInfo implements Validator {

    private String icp;
    private String webCm;
    private String robotRuleContent;

    public String getIcp() {
        return icp;
    }

    public void setIcp(String icp) {
        this.icp = icp;
    }

    public String getWebCm() {
        return webCm;
    }

    public void setWebCm(String webCm) {
        this.webCm = webCm;
    }

    // 允许的指令
    private static final Pattern VALID_LINE = Pattern.compile(
            "^(User-agent|Disallow|Allow|Sitemap|Crawl-delay|Host):\\s*.+$",
            Pattern.CASE_INSENSITIVE
    );

    private static boolean isValidLine(String line) {
        line = line.trim();

        // 忽略空行和注释
        if (line.isEmpty() || line.startsWith("#")) {
            return true;
        }

        return VALID_LINE.matcher(line).matches();
    }

    private static boolean isValidRobotsTxt(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (!isValidLine(line)) {
                return false; // 一行不合法就返回 false
            }
        }
        return true;
    }


    @Override
    public void doValid() {
        if (StringUtils.isNotEmpty(robotRuleContent) && !isValidRobotsTxt(robotRuleContent)) {
            throw new ArgsException("robots.txt");
        }
    }

    @Override
    public void doClean() {
        if (StringUtils.isNotEmpty(icp)) {
            this.icp = Jsoup.clean(icp, Safelist.basicWithImages());
        }
    }

    public String getRobotRuleContent() {
        return robotRuleContent;
    }

    public void setRobotRuleContent(String robotRuleContent) {
        this.robotRuleContent = robotRuleContent;
    }

    public static void main(String[] args) {
        boolean validRobotsTxt = isValidRobotsTxt("User-agent: * \n" +
                "Disallow: /\n# comment");
        System.out.println("validRobotsTxt = " + validRobotsTxt);
    }
}
