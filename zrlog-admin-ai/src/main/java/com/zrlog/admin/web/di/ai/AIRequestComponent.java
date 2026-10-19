package com.zrlog.admin.web.di.ai;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.web.controller.ai.*;

import dagger.BindsInstance;
import dagger.Subcomponent;

/** Each request owns its controllers and AI chat state. */
@Subcomponent
public interface AIRequestComponent {
    AIArticleController articles();

    AICommentController comments();

    AIWebSiteController settings();

    @Subcomponent.Factory
    interface Factory {
        AIRequestComponent create(@BindsInstance HttpRequest request);
    }
}
