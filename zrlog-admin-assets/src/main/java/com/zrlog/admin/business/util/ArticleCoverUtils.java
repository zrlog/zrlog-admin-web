package com.zrlog.admin.business.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.common.vo.AdminTokenVO;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArticleCoverUtils {


    private static final Logger LOGGER = LoggerUtil.getLogger(ArticleCoverUtils.class);

    private static byte[] getRequestBodyBytes(String url) throws IOException, URISyntaxException, InterruptedException {
        HttpFileHandle fileHandler = new HttpFileHandle("");
        HttpUtil.getInstance().sendGetRequest(url, new HashMap<>(), fileHandler, new HashMap<>());
        return IOUtil.getByteByInputStream(new FileInputStream(fileHandler.getT().getPath()));
    }

    public static String getFirstImgUrl(String htmlContent, HttpRequest httpRequest, AdminTokenVO adminTokenVO) {
        if (StringUtils.isEmpty(htmlContent)) {
            return "";
        }
        Elements elements = Jsoup.parse(htmlContent).select("img");
        if (elements.isEmpty()) {
            return null;
        }
        String url = elements.first().attr("src");
        try {
            String path = url;
            byte[] bytes;
            if (url.startsWith("https://") || url.startsWith("http://")) {
                path = URI.create(url).getPath();
                if (!path.startsWith(AdminConstants.ATTACHED_FOLDER)) {
                    path = (AdminConstants.ATTACHED_FOLDER + path).replace("//", "/");
                } else {
                    path = path.replace("//", "/");
                }
                bytes = getRequestBodyBytes(url);
                path = path.substring(0, path.indexOf('.')) + "_thumbnail" + path.substring(path.indexOf('.'));
            } else {
                bytes = IOUtil.getByteByInputStream(new FileInputStream(PathUtil.getStaticFile(url)));
                path = url.substring(0, url.indexOf('.')) + "_thumbnail" + url.substring(path.indexOf('.'));
            }
            File thumbnailFile = PathUtil.getStaticFile(path);
            if (bytes.length == 0) {
                return null;
            }
            int height = -1;
            int width = -1;
            //创建文件夹，避免保存失败
            thumbnailFile.getParentFile().mkdirs();
            IOUtil.writeBytesToFile(bytes, thumbnailFile);
            return new UploadService().getCloudUrl(path, thumbnailFile.getPath(), httpRequest,
                    adminTokenVO).getUrl() + "?h=" + height + "&w=" + width;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return null;
    }
}
