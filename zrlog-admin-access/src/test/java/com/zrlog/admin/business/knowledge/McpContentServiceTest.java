package com.zrlog.admin.business.knowledge;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.ContentToolModels.*;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.ArticlePinningService;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.business.service.ArticleVersionService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.admin.web.McpWebSetup;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;
import com.zrlog.model.Tag;
import com.zrlog.model.Type;
import com.zrlog.test.support.TestLogCapture;
import com.zrlog.util.I18nUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class McpContentServiceTest {

    private ZrLogConfig applicationConfig;

    @Parameterized.Parameters(name="database={0}") public static String[] databases() { return new String[]{"h2","sqlite"}; }
    private final String database;
    public McpContentServiceTest(String database) { this.database=database; }
    private InMemoryZrLogDatabase open() throws Exception { return database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open(); }
    private static final String CREATE = "{\"title\":\"Original\",\"typeId\":1,\"status\":\"draft\",\"markdown\":\"Original body\",\"content\":\"<p>Original body</p>\",\"keywords\":\"real,notes\",\"digest\":\"Original summary\",\"alias\":\"original\"}";
    private static JsonObject args(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static HttpRequest request() {
        return (HttpRequest)Proxy.newProxyInstance(McpContentServiceTest.class.getClassLoader(),new Class[]{HttpRequest.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "getUri": return "/mcp";
                case "getContextPath": return "/sub";
                case "getRemoteHost": return "127.0.0.1";
                case "getHeader": return null;
                case "getHeaderMap": case "getParamMap": return Map.of();
                default: return method.getReturnType().isPrimitive() ? 0 : null;
            }
        });
    }
    private String token(DataSourceWrapper testDataSource, String... permissions) throws Exception {
        PersonalTokenModels.Create body=new PersonalTokenModels.Create();body.name="MCP tools";
        body.permissionMode=permissions.length==0 ? "inherit" : "custom"; body.permissions=List.of(permissions);
        return new PersonalAccessTokenService(
                        new OAuthService(
                                        ApplicationServicesFixture.backend(applicationConfig)
                                                ::configured,
                                        ApplicationServicesFixture.available(
                                                applicationConfig, McpWebSetup.class),
                                        AccountServicesFixture.component(testDataSource).permissions(),
                                        AccountServicesFixture.component(testDataSource).security(),
                                        tokenResource ->
                                                new PersonalAccessTokenService(
                                                        tokenResource,
                                                        AccountServicesFixture.component(testDataSource)
                                                                .permissions(),
                                                        AccountServicesFixture.component(testDataSource)
                                                                .security()),
                                        AccountServicesFixture.component(testDataSource).backend())
                                .mcpResource(),
                        AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).security())
                .create(body)
                .token;
    }
    private McpContentService tools(String token, String language, ContentToolService content, AttachmentStorage storage, DataSourceWrapper testDataSource) {
        OAuthService oauth =
                new OAuthService(
                        ApplicationServicesFixture.backend(applicationConfig)::configured,
                        ApplicationServicesFixture.available(applicationConfig, McpWebSetup.class),
                        AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).security(),
                        tokenResource ->
                                new PersonalAccessTokenService(
                                        tokenResource,
                                        AccountServicesFixture.component(testDataSource).permissions(),
                                        AccountServicesFixture.component(testDataSource).security()),
                        AccountServicesFixture.component(testDataSource).backend());
        return new McpContentService(
                () -> {
                    try {
                        return oauth.authenticate(token, oauth.mcpResource(), Set.of());
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                request(),
                language,
                () -> new ContentToolFactory(
                        (toolScopes, toolRequest, toolLanguage, toolKnowledge) ->
                                new ContentToolProvider(
                                        toolScopes,
                                        toolRequest,
                                        toolLanguage,
                                        content,
                                        () -> storage,
                                        toolKnowledge,
                                        AccountServicesFixture.component(testDataSource).permissions()),
                        (toolActor, toolScopes, toolLanguage) ->
                                new KnowledgeService(
                                        toolActor,
                                        toolScopes,
                                        ApplicationServicesFixture.backend(applicationConfig)
                                                ::configured,
                                        () -> false,
                                        toolLanguage,
                                        () -> new Log(testDataSource)),
                        AccountServicesFixture.component(testDataSource).permissions()),
                ApplicationServicesFixture.backend(applicationConfig)::configured);
    }

    private McpContentService tools(String token, DataSourceWrapper testDataSource) {
        return tools(
                token,
                "en_US",
                new ContentToolService(
                        publishing(new InMemoryZrLogDatabase.TestCacheService(), testDataSource),
                        request -> {},
                        AccountServicesFixture.component(testDataSource).permissions(),
                        () -> new Tag(testDataSource),
                        () -> new Type(testDataSource)),
                null, testDataSource);
    }

    private ArticlePublishingService publishing(CacheService cache, DataSourceWrapper testDataSource) {
        return publishing(cache, testDataSource, ApplicationServicesFixture.audit(testDataSource));
    }

    private ArticlePublishingService publishing(CacheService cache, DataSourceWrapper testDataSource,
                                                AdminAuditService audit) {
        return new ArticlePublishingService(
                new AdminArticleService(
                        cache,
                        ApplicationServicesFixture.capability(
                                applicationConfig, ArticleAssistant.class),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource),
                                AccountServicesFixture.component(testDataSource).permissions()),
                        AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).preferences(),
                        new ArticleVersionService(
                                AccountServicesFixture.component(testDataSource).permissions(),
                                () -> new Log(testDataSource),
                                () -> new LogVersion(testDataSource)),
                        new ArticlePinningService(() -> new Log(testDataSource), ApplicationServicesFixture.locks(testDataSource)),
                        () -> new Log(testDataSource),
                        ApplicationServicesFixture.locks(testDataSource)),
                ApplicationServicesFixture.cacheRefresh(cache, applicationConfig),
                markdown -> null,
                audit,
                ApplicationServicesFixture.operations(testDataSource));
    }
    private Set<String> names(McpContentService tools) throws Exception { return tools.definitions("en_US").stream().map(t->t.name).collect(Collectors.toSet()); }

    @Test
    public void preservesEmojiCreationAndPublicationLogsWithLegacyUtf8Storage() throws Exception {
        try (InMemoryZrLogDatabase db = open();
             TestLogCapture logs = TestLogCapture.forClass(AdminAuditService.class)) {
            applicationConfig = db.config();
            WebsiteKvService auditStore = new WebsiteKvService(db.dataSource()) {
                @Override
                public boolean putString(String key, String value) throws SQLException {
                    // H2/SQLite accept supplementary characters; enforce MySQL utf8's limit here.
                    if (value.codePoints().anyMatch(codePoint -> codePoint > 0xffff)) {
                        throw new SQLException("Incorrect string value for website.value", "HY000", 1366);
                    }
                    return super.putString(key, value);
                }
            };
            AdminAuditService audit = new AdminAuditService(auditStore);
            McpContentService tools = tools(
                    token(db.dataSource()), "en_US",
                    new ContentToolService(
                            publishing(db.unitCacheService(), db.dataSource(), audit),
                            request -> {},
                            AccountServicesFixture.component(db.dataSource()).permissions(),
                            () -> new Tag(db.dataSource()),
                            () -> new Type(db.dataSource())),
                    null, db.dataSource());
            tools.call("create_article", args(CREATE));

            String title = "大理🌿 重庆🌶️ 家人👨‍👩‍👧 𠮷 \\uD83C\\uDF3F \"游记\"";
            JsonObject create = args(CREATE);
            create.addProperty("title", title);
            create.addProperty("alias", "emoji-audit");
            SavedArticle draft = (SavedArticle) tools.call("create_article", create);
            assertEquals("draft", draft.status);
            assertEquals(title, db.scalar("select title from log where logId=?", draft.id));
            assertEquals(2, audit.getRecentLogs().size());
            assertEquals(title, audit.getRecentLogs().get(0).getContent());

            SavedArticle published = (SavedArticle) tools.call("publish_article",
                    args("{\"id\":" + draft.id + ",\"version\":0}"));

            assertEquals("published", published.status);
            assertEquals(1, published.version);
            JsonArray stored = JsonParser.parseString(auditStore.getString("admin_audit_log")).getAsJsonArray();
            assertEquals(3, stored.size());
            assertEquals("PUBLISH_ARTICLE", stored.get(0).getAsJsonObject().get("action").getAsString());
            assertEquals("CREATE_ARTICLE", stored.get(1).getAsJsonObject().get("action").getAsString());
            assertEquals(title, stored.get(0).getAsJsonObject().get("content").getAsString());
            assertEquals(title, stored.get(1).getAsJsonObject().get("content").getAsString());
            assertEquals("Original", stored.get(2).getAsJsonObject().get("content").getAsString());
            assertEquals(title, audit.getRecentLogs().get(0).getContent());
            assertEquals(title, audit.getRecentLogs().get(1).getContent());
            assertEquals("MCP", audit.getRecentLogs().get(0).getBrowser());
            assertFalse(logs.contains(Level.WARNING, "Record admin audit log failed"));
        }
    }

    @Test
    public void reportsAuditDatabaseFailureWithoutLosingSuccessfulPublication() throws Exception {
        assertAuditFailureIsVisible(true);
    }

    @Test
    public void reportsAuditWriteWithNoAffectedRowsWithoutLosingSuccessfulPublication() throws Exception {
        assertAuditFailureIsVisible(false);
    }

    private void assertAuditFailureIsVisible(boolean throwsException) throws Exception {
        try (InMemoryZrLogDatabase db = open();
             TestLogCapture logs = TestLogCapture.forClass(AdminAuditService.class)) {
            applicationConfig = db.config();
            AtomicBoolean failAuditWrite = new AtomicBoolean();
            WebsiteKvService auditStore = new WebsiteKvService(db.dataSource()) {
                @Override
                public boolean putString(String key, String value) throws SQLException {
                    if (failAuditWrite.get()) {
                        if (throwsException) throw new SQLException("Audit database unavailable", "08006");
                        return false;
                    }
                    return super.putString(key, value);
                }
            };
            McpContentService tools = tools(
                    token(db.dataSource()), "en_US",
                    new ContentToolService(
                            publishing(db.unitCacheService(), db.dataSource(), new AdminAuditService(auditStore)),
                            request -> {},
                            AccountServicesFixture.component(db.dataSource()).permissions(),
                            () -> new Tag(db.dataSource()),
                            () -> new Type(db.dataSource())),
                    null, db.dataSource());
            SavedArticle draft = (SavedArticle) tools.call("create_article", args(CREATE));
            String previousAudit = auditStore.getString("admin_audit_log");
            assertFalse(logs.contains(Level.WARNING, "Record admin audit log failed"));

            failAuditWrite.set(true);
            SavedArticle published = (SavedArticle) tools.call("publish_article",
                    args("{\"id\":" + draft.id + ",\"version\":0}"));

            assertEquals("published", published.status);
            assertEquals(1, published.version);
            assertFalse(AccountAccess.truth(db.scalar("select rubbish from log where logId=?", draft.id)));
            assertEquals(previousAudit, auditStore.getString("admin_audit_log"));
            assertTrue("A missing audit entry must produce a diagnostic warning",
                    logs.contains(Level.WARNING, "Record admin audit log failed"));
            assertTrue(logs.contains(Level.WARNING, "action=PUBLISH_ARTICLE"));
            assertTrue(logs.contains(Level.WARNING, "uri=/mcp"));
        }
    }

    @Test public void createsPatchesAndPublishesThroughVersionAuditAndCacheServices() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            AtomicInteger refreshes=new AtomicInteger();
            McpContentService tools =
                    tools(
                            token(db.dataSource()),
                            "en_US",
                            new ContentToolService(
                                    publishing(new InMemoryZrLogDatabase.TestCacheService(), db.dataSource()),
                                    request -> refreshes.incrementAndGet(),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    () -> new Tag(db.dataSource()),
                                    () -> new Type(db.dataSource())),
                            null, db.dataSource());
            var browser=AdminTokenThreadLocal.getUser();
            SavedArticle saved=(SavedArticle)tools.call("create_article",args(CREATE));
            assertEquals("draft",saved.status);assertEquals(0,saved.version);assertEquals(0,refreshes.get());
            ArticleDetails detail=(ArticleDetails)tools.call("get_article",args("{\"id\":"+saved.id+"}"));
            assertEquals("Original",detail.title);assertEquals(0,detail.version);assertEquals(1,detail.typeId);
            SavedArticle patched=(SavedArticle)tools.call("update_article",args("{\"id\":"+saved.id+",\"version\":0,\"title\":\"Revised\"}"));
            assertEquals(1,patched.version);
            Map<String,Object> row=db.queryOne("select * from log where logId=?",saved.id);
            assertEquals("Original body",row.get("markdown"));assertEquals("<p>Original body</p>",row.get("content"));
            assertEquals("real,notes",row.get("keywords"));assertEquals("Original summary",row.get("digest"));
            JsonArray draftAudit = JsonParser.parseString(String.valueOf(db.scalar("select value from website where name='admin_audit_log'"))).getAsJsonArray();
            assertEquals(1, draftAudit.size());
            assertEquals("CREATE_ARTICLE", draftAudit.get(0).getAsJsonObject().get("action").getAsString());
            assertEquals("Original", draftAudit.get(0).getAsJsonObject().get("content").getAsString());
            SavedArticle published=(SavedArticle)tools.call("publish_article",args("{\"id\":"+saved.id+",\"version\":1}"));
            assertEquals("published",published.status);assertEquals(2,published.version);assertEquals("updated",published.refreshStatus);
            assertEquals(1,refreshes.get());assertFalse(AccountAccess.truth(db.scalar("select rubbish from log where logId=?",saved.id)));
            String audit=String.valueOf(db.scalar("select value from website where name='admin_audit_log'"));
            assertEquals(2, JsonParser.parseString(audit).getAsJsonArray().size());
            assertTrue(audit.contains("CREATE_ARTICLE"));assertTrue(audit.contains("PUBLISH_ARTICLE"));
            assertEquals(
                    "MCP",
                    new AdminAuditService(ApplicationServicesFixture.websiteKv(db.dataSource()))
                            .getRecentLogs()
                            .get(0)
                            .getBrowser());
            for(int version:new int[]{0,1,99}) assertThrows(UpdateArticleExpireException.class,()->tools.call("update_article",args("{\"id\":"+saved.id+",\"version\":"+version+",\"title\":\"Stale\"}")));
            JsonObject conflict =
                    new McpService(ApplicationServicesFixture.siteTitle(db.config()), "en_US")
                            .handle(
                                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"publish_article\",\"arguments\":{\"id\":"
                                            + saved.id
                                            + ",\"version\":0}}}",
                                    tools)
                            .body
                            .getAsJsonObject("result");
            assertTrue(conflict.get("isError").getAsBoolean());
            assertEquals("ADMIN_ARTICLE_UPDATE_EXPIRED",conflict.getAsJsonObject("structuredContent").get("code").getAsString());
            assertEquals("Revised",db.scalar("select title from log where logId=?",saved.id));
            assertEquals(audit, db.scalar("select value from website where name='admin_audit_log'"));
            assertSame(browser,AdminTokenThreadLocal.getUser());
        }
    }
    @Test public void keepsSuccessfulSaveWhenStaticRefreshFailsAndAllowsExplicitDirectPublication() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            McpContentService tools =
                    tools(
                            token(db.dataSource()),
                            "zh_CN",
                            new ContentToolService(
                                    publishing(new InMemoryZrLogDatabase.TestCacheService(), db.dataSource()),
                                    request -> {
                                        throw new IllegalStateException("internal detail");
                                    },
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    () -> new Tag(db.dataSource()),
                                    () -> new Type(db.dataSource())),
                            null, db.dataSource());
            JsonObject input=args(CREATE);input.addProperty("status","published");
            JsonObject rpc=new JsonObject();rpc.addProperty("jsonrpc","2.0");rpc.addProperty("id",1);rpc.addProperty("method","tools/call");
            JsonObject params=new JsonObject();params.addProperty("name","create_article");params.add("arguments",input);rpc.add("params",params);
            JsonObject result =
                    new McpService(ApplicationServicesFixture.siteTitle(db.config()), "zh_CN")
                            .handle(rpc.toString(), tools)
                            .body
                            .getAsJsonObject("result");
            assertFalse(result.get("isError").getAsBoolean());
            JsonObject saved=result.getAsJsonObject("structuredContent");assertEquals("failed",saved.get("refreshStatus").getAsString());
            assertTrue(saved.get("warning").getAsString().contains("文章已保存"));assertFalse(saved.toString().contains("internal detail"));
            assertEquals(1,((Number)db.scalar("select count(*) from log")).intValue());
            JsonArray audit = JsonParser.parseString(String.valueOf(db.scalar("select value from website where name='admin_audit_log'"))).getAsJsonArray();
            assertEquals(2, audit.size());
            assertEquals("PUBLISH_ARTICLE", audit.get(0).getAsJsonObject().get("action").getAsString());
            assertEquals("CREATE_ARTICLE", audit.get(1).getAsJsonObject().get("action").getAsString());
        }
    }
    @Test public void replacesBodyWithoutLeavingStaleMarkdownOrHtml() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            applicationConfig = db.config();
            McpContentService tools = tools(token(db.dataSource()), db.dataSource());
            SavedArticle saved = (SavedArticle) tools.call("create_article", args(CREATE));
            tools.call("update_article", args("{\"id\":" + saved.id + ",\"version\":0,\"content\":\"<p>HTML replacement</p>\"}"));
            Map<String, Object> row = db.queryOne("select * from log where logId=?", saved.id);
            assertEquals("", row.get("markdown"));
            assertEquals("html", row.get("editor_type"));
            assertEquals("<p>HTML replacement</p>", row.get("content"));
            tools.call("update_article", args("{\"id\":" + saved.id + ",\"version\":1,\"markdown\":\"Markdown replacement\",\"content\":\"<p>Markdown replacement</p>\"}"));
            row = db.queryOne("select * from log where logId=?", saved.id);
            assertEquals("Markdown replacement", row.get("markdown"));
            assertEquals("markdown", row.get("editor_type"));
            assertEquals("<p>Markdown replacement</p>", row.get("content"));
            tools.call("update_article", args("{\"id\":" + saved.id + ",\"version\":2,\"markdown\":\"\"}"));
            row = db.queryOne("select * from log where logId=?", saved.id);
            assertEquals("", row.get("markdown"));
            assertEquals("", row.get("content"));
        }
    }
    @Test public void filtersActionsAndDeniesHiddenToolsWithoutWideningOldReadTokens() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            String writer=token(db.dataSource(), "article.create", "article.update");McpContentService tools=tools(writer, db.dataSource());
            Object auditBefore = db.scalar("select value from website where name='admin_audit_log'");
            assertEquals(Set.of("create_article","update_article"),names(tools));
            JsonObject published=args(CREATE);published.addProperty("status","published");
            assertThrows(PermissionErrorException.class,()->tools.call("create_article",published));
            assertThrows(PermissionErrorException.class,()->tools.call("publish_article",args("{\"id\":1,\"version\":0}")));
            assertThrows(PermissionErrorException.class,()->tools.call("list_categories",new JsonObject()));
            assertEquals(auditBefore, db.scalar("select value from website where name='admin_audit_log'"));
            assertEquals("draft",((SavedArticle)tools.call("create_article",args(CREATE))).status);
            PersonalTokenModels.Create legacy=new PersonalTokenModels.Create();legacy.name="old";legacy.scopes=List.of("articles:read");
            String old =
                    new PersonalAccessTokenService(
                                    new OAuthService(
                                                    ApplicationServicesFixture.backend(db.config())
                                                            ::configured,
                                                    ApplicationServicesFixture.available(
                                                            db.config(), McpWebSetup.class),
                                                    AccountServicesFixture.component(db.dataSource())
                                                            .permissions(),
                                                    AccountServicesFixture.component(db.dataSource()).security(),
                                                    tokenResource ->
                                                            new PersonalAccessTokenService(
                                                                    tokenResource,
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .permissions(),
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .security()),
                                                    AccountServicesFixture.component(db.dataSource()).backend())
                                            .mcpResource(),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    AccountServicesFixture.component(db.dataSource()).security())
                            .create(legacy)
                            .token;
            assertEquals(Set.of("search_articles","read_article","get_article"),names(tools(old, db.dataSource())));
            assertThrows(PermissionErrorException.class,()->tools(old, db.dataSource()).call("create_article",args(CREATE)));
            assertEquals(Set.of("list_categories","list_tags"),names(tools(token(db.dataSource(), "taxonomy.read"), db.dataSource())));
            db.execute("update user_access_token set revoked=? where tokenHash=?",true,OAuthService.hash(writer));
            assertThrows(OAuthException.class,()->tools.call("create_article",args(CREATE)));
        }
    }
    @Test public void respectsOwnershipPrivateArticlesAndContributorLimits() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            db.execute("insert into user(userId,userName,role) values(2,'other','author')");
            db.execute("insert into log(logId,userId,typeId,title,markdown,content,rubbish,privacy,version) values(10,2,1,'Other','Text','<p>Text</p>',true,false,0)");
            db.execute("update user set role='author' where userId=1");
            McpContentService author=tools(token(db.dataSource()), db.dataSource());
            assertThrows(PermissionErrorException.class,()->author.call("update_article",args("{\"id\":10,\"version\":0,\"title\":\"Changed\"}")));
            db.execute("update user set role='editor' where userId=1");
            McpContentService editor=tools(token(db.dataSource()), db.dataSource());
            assertEquals(1,((SavedArticle)editor.call("update_article",args("{\"id\":10,\"version\":0,\"title\":\"Changed\"}"))).version);
            db.execute("update log set privacy=true where logId=10");
            assertThrows(PermissionErrorException.class,()->editor.call("get_article",args("{\"id\":10}")));
            assertThrows(PermissionErrorException.class,()->editor.call("publish_article",args("{\"id\":10,\"version\":1}")));
            db.execute("update user set role='contributor' where userId=1");
            McpContentService contributor=tools(token(db.dataSource()), db.dataSource());
            assertFalse(names(contributor).contains("publish_article"));
            assertEquals("draft",((SavedArticle)contributor.call("create_article",args(CREATE))).status);
            for(String status:List.of("private","published")) {
                JsonObject input=args(CREATE);input.addProperty("status",status);
                assertThrows(PermissionErrorException.class,()->contributor.call("create_article",input));
            }
            db.execute("update user set enabled=false where userId=1");
            assertThrows(OAuthException.class,()->contributor.definitions("en_US"));
        }
    }
    @Test public void validatesFieldsBeforeWritingAndPaginatesTaxonomyWithoutArticleCounts() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            McpContentService tools=tools(token(db.dataSource()), db.dataSource());
            for(String invalid:List.of("{\"title\":\"A\",\"typeId\":1}","{\"title\":\"A\",\"typeId\":1.5,\"status\":\"draft\"}",
                    "{\"title\":\"A\",\"typeId\":1,\"status\":\"draft\",\"userId\":2}","{\"id\":1,\"version\":0}")) {
                assertThrows(IllegalArgumentException.class,()->tools.call(invalid.contains("version")?"update_article":"create_article",args(invalid)));
            }
            JsonObject missing=args(CREATE);missing.addProperty("typeId",999);assertThrows(IllegalArgumentException.class,()->tools.call("create_article",missing));
            assertEquals(0,((Number)db.scalar("select count(*) from log")).intValue());
            db.execute("insert into type(typeId,typeName,alias) values(2,'Second','second')");
            TaxonomyList first=(TaxonomyList)tools.call("list_categories",args("{\"limit\":1}"));
            assertEquals(1,first.items.size());assertEquals(Integer.valueOf(1),first.nextOffset);
            TaxonomyList second=(TaxonomyList)tools.call("list_categories",args("{\"offset\":1,\"limit\":1}"));
            assertEquals("Second",second.items.get(0).name);assertNull(second.nextOffset);
            db.execute("insert into tag(tagId,text,count) values(1,'java',20)");
            TaxonomyList tags=(TaxonomyList)tools.call("list_tags",new JsonObject());assertEquals("java",tags.items.get(0).name);
            assertFalse(new Gson().toJson(tags).contains("count"));
        }
    }
    @Test public void uploadsOnlyBoundedClientBytesAndHonorsDisabledStorage() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            applicationConfig = db.config();
            List<byte[]> uploaded=new ArrayList<>();
            AttachmentStorage storage=new AttachmentStorage() {
                public void setup() { }
                public String saveAttachment(byte[] bytes,String filename,HttpRequest request) {
                    uploaded.add(bytes);return request.getContextPath()+"/attached/users/1/file.png";
                }
            };
            String token = token(db.dataSource(), "asset.upload");
            McpContentService tools =
                    tools(
                            token,
                            "en_US",
                            new ContentToolService(
                                    new ArticlePublishingService(
                                            new AdminArticleService(
                                                    db.unitCacheService(),
                                                    ApplicationServicesFixture.capability(
                                                            db.config(), ArticleAssistant.class),
                                                    new AdminPageDataFactory(
                                                            ApplicationServicesFixture.site(
                                                                    db.config()),
                                                            ApplicationServicesFixture.buildId(
                                                                    db.resources()),
                                                            ApplicationServicesFixture.messages(db.dataSource()),
                                                            AccountServicesFixture.component(db.dataSource())
                                                                    .permissions()),
                                                    AccountServicesFixture.component(db.dataSource())
                                                            .permissions(),
                                                    AccountServicesFixture.component(db.dataSource())
                                                            .preferences(),
                                                    new ArticleVersionService(
                                                            AccountServicesFixture.component(db.dataSource())
                                                                    .permissions(),
                                                            () -> new Log(db.dataSource()),
                                                            () ->
                                                                    new LogVersion(
                                                                            db.dataSource())),
                                                    new ArticlePinningService(
                                                            () -> new Log(db.dataSource()),
                                                            ApplicationServicesFixture.locks(db.dataSource())),
                                                    () -> new Log(db.dataSource()),
                                                    ApplicationServicesFixture.locks(db.dataSource())),
                                            ApplicationServicesFixture.cacheRefresh(
                                                    db.unitCacheService(), db.config()),
                                            markdown -> null,
                                            ApplicationServicesFixture.audit(db.dataSource()),
                                            ApplicationServicesFixture.operations(db.dataSource())),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    () -> new Tag(db.dataSource()),
                                    () -> new Type(db.dataSource())),
                            storage, db.dataSource());
            assertEquals(Set.of("upload_attachment"),names(tools));
            Attachment result=(Attachment)tools.call("upload_attachment",args("{\"filename\":\"cover.png\",\"data\":\"aGVsbG8=\"}"));
            assertEquals("/sub/attached/users/1/file.png",result.url);assertEquals(5,result.size);assertArrayEquals("hello".getBytes(),uploaded.get(0));
            for(String filename:List.of("../cover.png","/tmp/cover.png","dir\\cover.png","cover","a.png?x","a.png#x")) {
                JsonObject input=new JsonObject();input.addProperty("filename",filename);input.addProperty("data","aA==");
                assertThrows(IllegalArgumentException.class,()->tools.call("upload_attachment",input));
            }
            for(String data:List.of("%%%","","data:image/png;base64,aA==",Base64.getEncoder().encodeToString(new byte[ContentToolCatalog.MAX_ATTACHMENT_BYTES+1]))) {
                JsonObject input=new JsonObject();input.addProperty("filename","a.png");input.addProperty("data",data);
                assertThrows(IllegalArgumentException.class,()->tools.call("upload_attachment",input));
            }
            assertEquals(1,uploaded.size());assertTrue(names(tools(token, db.dataSource())).isEmpty());
            assertThrows(PermissionErrorException.class,()->tools(token, db.dataSource()).call("upload_attachment",args("{\"filename\":\"a.png\",\"data\":\"aA==\"}")));
        }
    }
    @Test public void localizesEveryNewDefinitionAndErrorWithoutLeakingThreadLanguage() throws Exception {
        try(InMemoryZrLogDatabase db=open();var ignored=AdminLanguageContext.open("en_US")) {
            applicationConfig = db.config();
            for(String language:List.of("zh_CN","en_US")) {
                for(var tool:ContentToolCatalog.tools(language)) {
                    assertEquals(language.equals("zh_CN"),tool.description.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff));
                    for(var property:tool.inputSchema.getAsJsonObject("properties").entrySet()) assertFalse(property.getValue().getAsJsonObject().get("description").getAsString().isBlank());
                }
                McpContentService tools =
                        tools(
                                token(db.dataSource(), "article.read"),
                                language,
                                new ContentToolService(
                                        new ArticlePublishingService(
                                                new AdminArticleService(
                                                        db.unitCacheService(),
                                                        ApplicationServicesFixture.capability(
                                                                applicationConfig,
                                                                ArticleAssistant.class),
                                                        new AdminPageDataFactory(
                                                                ApplicationServicesFixture.site(
                                                                        applicationConfig),
                                                                () -> "test",
                                                                ApplicationServicesFixture
                                                                        .messages(db.dataSource()),
                                                                AccountServicesFixture.component(db.dataSource())
                                                                        .permissions()),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .permissions(),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .preferences(),
                                                        new ArticleVersionService(
                                                                AccountServicesFixture.component(db.dataSource())
                                                                        .permissions(),
                                                                () -> new Log(db.dataSource()),
                                                                () ->
                                                                        new LogVersion(
                                                                                db.dataSource())),
                                                        new ArticlePinningService(
                                                                () -> new Log(db.dataSource()),
                                                                ApplicationServicesFixture.locks(db.dataSource())),
                                                        () -> new Log(db.dataSource()),
                                                        ApplicationServicesFixture.locks(db.dataSource())),
                                                ApplicationServicesFixture.cacheRefresh(
                                                        db.unitCacheService(), applicationConfig),
                                                markdown -> null,
                                                ApplicationServicesFixture.audit(db.dataSource()),
                                                ApplicationServicesFixture.operations(db.dataSource())),
                                        AccountServicesFixture.component(db.dataSource()).permissions(),
                                        () -> new Tag(db.dataSource()),
                                        () -> new Type(db.dataSource())),
                                null, db.dataSource());
                JsonObject result =
                        new McpService(
                                        ApplicationServicesFixture.siteTitle(applicationConfig),
                                        language)
                                .handle(
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"publish_article\",\"arguments\":{\"id\":1,\"version\":0}}}",
                                        tools)
                                .body
                                .getAsJsonObject("result");
                assertTrue(result.get("isError").getAsBoolean());
                assertEquals("ADMIN_PERMISSION_DENIED",result.getAsJsonObject("structuredContent").get("code").getAsString());
                assertEquals(language.equals("zh_CN"),result.getAsJsonObject("structuredContent").get("error").getAsString().codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff));
                assertEquals("en_US",I18nUtil.getCurrentLocale());
            }
        }
    }
}
