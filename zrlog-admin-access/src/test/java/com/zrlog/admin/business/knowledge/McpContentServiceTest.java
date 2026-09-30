package com.zrlog.admin.business.knowledge;

import com.google.gson.*;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.ContentToolModels.*;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.I18nUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class McpContentServiceTest {
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
    private String token(String... permissions) throws Exception {
        PersonalTokenModels.Create body=new PersonalTokenModels.Create();body.name="MCP tools";
        body.permissionMode=permissions.length==0 ? "inherit" : "custom"; body.permissions=List.of(permissions);
        return new PersonalAccessTokenService(new OAuthService().mcpResource()).create(body).token;
    }
    private McpContentService tools(String token, String language, ContentToolService content, AttachmentStorage storage) {
        OAuthService oauth=new OAuthService();
        return new McpContentService(()->{
            try { return oauth.authenticate(token,oauth.mcpResource(),Set.of()); }
            catch (java.sql.SQLException e) { throw new RuntimeException(e); }
        },request(),language,content,()->storage);
    }
    private McpContentService tools(String token) { return tools(token,"en_US",new ContentToolService(request -> {}),null); }
    private Set<String> names(McpContentService tools) throws Exception { return tools.definitions("en_US").stream().map(t->t.name).collect(Collectors.toSet()); }

    @Test public void createsPatchesAndPublishesThroughVersionAuditAndCacheServices() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            AtomicInteger refreshes=new AtomicInteger();
            McpContentService tools=tools(token(),"en_US",new ContentToolService(request->refreshes.incrementAndGet()),null);
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
            SavedArticle published=(SavedArticle)tools.call("publish_article",args("{\"id\":"+saved.id+",\"version\":1}"));
            assertEquals("published",published.status);assertEquals(2,published.version);assertEquals("updated",published.refreshStatus);
            assertEquals(1,refreshes.get());assertFalse(AccountAccess.truth(db.scalar("select rubbish from log where logId=?",saved.id)));
            String audit=String.valueOf(db.scalar("select value from website where name='admin_audit_log'"));
            assertTrue(audit.contains("CREATE_ARTICLE"));assertTrue(audit.contains("UPDATE_ARTICLE"));
            assertEquals("MCP", new AdminAuditService().getRecentLogs().get(0).getBrowser());
            for(int version:new int[]{0,1,99}) assertThrows(UpdateArticleExpireException.class,()->tools.call("update_article",args("{\"id\":"+saved.id+",\"version\":"+version+",\"title\":\"Stale\"}")));
            JsonObject conflict = new McpService("en_US").handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"publish_article\",\"arguments\":{\"id\":"+saved.id+",\"version\":0}}}",tools).body.getAsJsonObject("result");
            assertTrue(conflict.get("isError").getAsBoolean());
            assertEquals("ADMIN_ARTICLE_UPDATE_EXPIRED",conflict.getAsJsonObject("structuredContent").get("code").getAsString());
            assertEquals("Revised",db.scalar("select title from log where logId=?",saved.id));
            assertSame(browser,AdminTokenThreadLocal.getUser());
        }
    }
    @Test public void keepsSuccessfulSaveWhenStaticRefreshFailsAndAllowsExplicitDirectPublication() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            McpContentService tools=tools(token(),"zh_CN",new ContentToolService(request->{throw new IllegalStateException("internal detail");}),null);
            JsonObject input=args(CREATE);input.addProperty("status","published");
            JsonObject rpc=new JsonObject();rpc.addProperty("jsonrpc","2.0");rpc.addProperty("id",1);rpc.addProperty("method","tools/call");
            JsonObject params=new JsonObject();params.addProperty("name","create_article");params.add("arguments",input);rpc.add("params",params);
            JsonObject result=new McpService("zh_CN").handle(rpc.toString(),tools).body.getAsJsonObject("result");
            assertFalse(result.get("isError").getAsBoolean());
            JsonObject saved=result.getAsJsonObject("structuredContent");assertEquals("failed",saved.get("refreshStatus").getAsString());
            assertTrue(saved.get("warning").getAsString().contains("文章已保存"));assertFalse(saved.toString().contains("internal detail"));
            assertEquals(1,((Number)db.scalar("select count(*) from log")).intValue());
        }
    }
    @Test public void replacesBodyWithoutLeavingStaleMarkdownOrHtml() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            McpContentService tools = tools(token());
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
            String writer=token("article.create","article.update");McpContentService tools=tools(writer);
            assertEquals(Set.of("create_article","update_article"),names(tools));
            JsonObject published=args(CREATE);published.addProperty("status","published");
            assertThrows(PermissionErrorException.class,()->tools.call("create_article",published));
            assertThrows(PermissionErrorException.class,()->tools.call("publish_article",args("{\"id\":1,\"version\":0}")));
            assertThrows(PermissionErrorException.class,()->tools.call("list_categories",new JsonObject()));
            assertEquals("draft",((SavedArticle)tools.call("create_article",args(CREATE))).status);
            PersonalTokenModels.Create legacy=new PersonalTokenModels.Create();legacy.name="old";legacy.scopes=List.of("articles:read");
            String old=new PersonalAccessTokenService(new OAuthService().mcpResource()).create(legacy).token;
            assertEquals(Set.of("search_articles","read_article","get_article"),names(tools(old)));
            assertThrows(PermissionErrorException.class,()->tools(old).call("create_article",args(CREATE)));
            assertEquals(Set.of("list_categories","list_tags"),names(tools(token("taxonomy.read"))));
            db.execute("update user_access_token set revoked=? where tokenHash=?",true,OAuthService.hash(writer));
            assertThrows(OAuthException.class,()->tools.call("create_article",args(CREATE)));
        }
    }
    @Test public void respectsOwnershipPrivateArticlesAndContributorLimits() throws Exception {
        try(InMemoryZrLogDatabase db=open()) {
            db.execute("insert into user(userId,userName,role) values(2,'other','author')");
            db.execute("insert into log(logId,userId,typeId,title,markdown,content,rubbish,privacy,version) values(10,2,1,'Other','Text','<p>Text</p>',true,false,0)");
            db.execute("update user set role='author' where userId=1");
            McpContentService author=tools(token());
            assertThrows(PermissionErrorException.class,()->author.call("update_article",args("{\"id\":10,\"version\":0,\"title\":\"Changed\"}")));
            db.execute("update user set role='editor' where userId=1");
            McpContentService editor=tools(token());
            assertEquals(1,((SavedArticle)editor.call("update_article",args("{\"id\":10,\"version\":0,\"title\":\"Changed\"}"))).version);
            db.execute("update log set privacy=true where logId=10");
            assertThrows(PermissionErrorException.class,()->editor.call("get_article",args("{\"id\":10}")));
            assertThrows(PermissionErrorException.class,()->editor.call("publish_article",args("{\"id\":10,\"version\":1}")));
            db.execute("update user set role='contributor' where userId=1");
            McpContentService contributor=tools(token());
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
            McpContentService tools=tools(token());
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
            List<byte[]> uploaded=new ArrayList<>();
            AttachmentStorage storage=new AttachmentStorage() {
                public void setup() { }
                public String saveAttachment(byte[] bytes,String filename,HttpRequest request) {
                    uploaded.add(bytes);return request.getContextPath()+"/attached/users/1/file.png";
                }
            };
            String token=token("asset.upload");McpContentService tools=tools(token,"en_US",new ContentToolService(),storage);
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
            assertEquals(1,uploaded.size());assertTrue(names(tools(token)).isEmpty());
            assertThrows(PermissionErrorException.class,()->tools(token).call("upload_attachment",args("{\"filename\":\"a.png\",\"data\":\"aA==\"}")));
        }
    }
    @Test public void localizesEveryNewDefinitionAndErrorWithoutLeakingThreadLanguage() throws Exception {
        try(InMemoryZrLogDatabase db=open();var ignored=AdminLanguageContext.open("en_US")) {
            for(String language:List.of("zh_CN","en_US")) {
                for(var tool:ContentToolCatalog.tools(language)) {
                    assertEquals(language.equals("zh_CN"),tool.description.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff));
                    for(var property:tool.inputSchema.getAsJsonObject("properties").entrySet()) assertFalse(property.getValue().getAsJsonObject().get("description").getAsString().isBlank());
                }
                McpContentService tools=tools(token("article.read"),language,new ContentToolService(),null);
                JsonObject result=new McpService(language).handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"publish_article\",\"arguments\":{\"id\":1,\"version\":0}}}",tools).body.getAsJsonObject("result");
                assertTrue(result.get("isError").getAsBoolean());
                assertEquals("ADMIN_PERMISSION_DENIED",result.getAsJsonObject("structuredContent").get("code").getAsString());
                assertEquals(language.equals("zh_CN"),result.getAsJsonObject("structuredContent").get("error").getAsString().codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff));
                assertEquals("en_US",I18nUtil.getCurrentLocale());
            }
        }
    }
}
