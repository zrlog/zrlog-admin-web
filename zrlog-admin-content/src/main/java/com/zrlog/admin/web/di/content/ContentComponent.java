package com.zrlog.admin.web.di.content;

import com.zrlog.admin.business.content.*;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.service.MarkdownRenderer;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Singleton;

import java.util.function.Supplier;

@Singleton
@Component(
        modules = {
            DataBindingsModule.class,
            ServiceBindingsModule.class,
            AccountBindingsModule.class,
            ContentModule.class
        })
public interface ContentComponent {
    AdminArticleService articles();

    ArticlePublishingService publishing();

    ContentToolService contentTools();

    ContentToolFactory tools();

    AdminArticleController adminArticleController();

    AdminArticlePinningController adminArticlePinningController();

    AdminArticleVersionController adminArticleVersionController();

    AdminTagController adminTagController();

    BlogNavController blogNavController();

    CommentController commentController();

    LinkController linkController();

    LinkPreviewController linkPreviewController();

    PersonalDataController personalDataController();

    TypeController typeController();

    @Component.Factory
    interface Factory {
        ContentComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance Supplier<ArticleAssistant> assistant,
                @BindsInstance Supplier<AttachmentStorage> storage,
                @BindsInstance AdminStaticSiteSsePublisher staticSites,
                @BindsInstance MarkdownRenderer markdown);
    }
}
