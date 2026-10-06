package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import java.util.*;

public final class AccountNativeImageUtils {
    private AccountNativeImageUtils() { }
    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.base.UserPreferences.class,
                com.zrlog.admin.business.rest.base.UserPreferences.Appearance.class,
                com.zrlog.admin.business.rest.base.UserPreferences.Assistant.class,
                com.zrlog.admin.business.rest.base.UserPreferences.Editor.class,
                com.zrlog.admin.business.rest.base.UserPreferences.Session.class,
                com.zrlog.admin.business.rest.base.UserPreferences.ArticleList.class,
                com.zrlog.admin.business.rest.request.LoginRequest.class,
                com.zrlog.admin.business.rest.request.UpdateAdminRequest.class,
                com.zrlog.admin.business.rest.request.UpdatePasswordRequest.class,
                com.zrlog.admin.business.rest.request.UpdateMfaRequest.class,
                com.zrlog.admin.business.rest.request.FirstUseChecklistRequest.class,
                com.zrlog.admin.business.rest.request.PasskeyAuthenticationVerifyRequest.class,
                com.zrlog.admin.business.rest.request.PasskeyRegistrationOptionsRequest.class,
                com.zrlog.admin.business.rest.request.PasskeyRegistrationVerifyRequest.class,
                com.zrlog.admin.business.rest.request.PasskeyRemoveRequest.class,
                com.zrlog.admin.business.rest.request.PasskeyCredential.class,
                com.zrlog.admin.business.rest.request.PasskeyCredential.AuthenticatorResponse.class,
                com.zrlog.admin.business.service.PasskeyService.ChallengeState.class,
                com.zrlog.admin.business.rest.response.UserPreferencesResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardCardConfigResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardCardResponse.class,
                com.zrlog.admin.business.rest.response.UserInfoResponse.class,
                com.zrlog.admin.business.rest.response.UserBasicInfoResponse.class,
                com.zrlog.admin.business.rest.response.MfaStatusResponse.class,
                com.zrlog.admin.business.rest.response.PasskeyOptionsResponse.class,
                com.zrlog.admin.business.rest.response.PasskeyCredentialDescriptor.class,
                com.zrlog.admin.business.rest.response.PasskeyRegistrationOptionsResponse.class,
                com.zrlog.admin.business.rest.response.PasskeyRegistrationOptionsResponse.RelyingParty.class,
                com.zrlog.admin.business.rest.response.PasskeyRegistrationOptionsResponse.User.class,
                com.zrlog.admin.business.rest.response.PasskeyRegistrationOptionsResponse.CredentialParameter.class,
                com.zrlog.admin.business.rest.response.PasskeyRegistrationOptionsResponse.AuthenticatorSelection.class,
                com.zrlog.admin.business.rest.response.PasskeyAuthenticationOptionsResponse.class,
                com.zrlog.admin.business.rest.response.PasskeySummaryResponse.class,
                com.zrlog.admin.business.rest.response.AdminPageDataResponse.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.security.AccessModels.class.getDeclaredClasses()));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.security.MemberModels.class.getDeclaredClasses()));
    }
}
