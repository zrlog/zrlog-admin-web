package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.exception.OldPasswordException;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class PasskeyRegistrationOptionsRequest implements Validator {

    private String password;
    private String mfaCode;
    private String name;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(password)) {
            throw new OldPasswordException();
        }
        if (StringUtils.isNotEmpty(name) && name.length() > 64) {
            throw new ArgsException("name");
        }
    }

    @Override
    public void doClean() {
        if (StringUtils.isNotEmpty(name)) {
            name = Jsoup.clean(name, Safelist.none()).trim();
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMfaCode() {
        return mfaCode;
    }

    public void setMfaCode(String mfaCode) {
        this.mfaCode = mfaCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
