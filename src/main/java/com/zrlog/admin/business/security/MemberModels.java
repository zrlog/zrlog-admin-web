package com.zrlog.admin.business.security;

import java.util.List;

public final class MemberModels {
    private MemberModels() { }
    public static class Member {
        public int userId;
        public String userName;
        public String email;
        public String role;
        public boolean enabled;
    }
    public static class Page {
        public List<Member> members;
        public String currentRole;
    }
    public static class Create implements com.zrlog.common.Validator {
        public void doValid() { if (userName == null || role == null || password == null) throw new com.zrlog.common.exception.ArgsException(); }
        public String userName;
        public String email;
        public String password;
        public String role;
    }
    public static class Transfer implements com.zrlog.common.Validator {
        public Integer userId;
        public String password;
        public String mfaCode;
        public void doValid() { if (userId == null || password == null) throw new com.zrlog.common.exception.ArgsException(); }
    }
    public static class Update implements com.zrlog.common.Validator {
        public void doValid() { if (userId == null || role == null || enabled == null) throw new com.zrlog.common.exception.ArgsException(); }
        public Integer userId;
        public String role;
        public Boolean enabled;
        public String password;
    }
}
