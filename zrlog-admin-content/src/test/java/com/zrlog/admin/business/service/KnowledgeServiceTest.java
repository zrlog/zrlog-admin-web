package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class KnowledgeServiceTest {
    @org.junit.runners.Parameterized.Parameters(name="sqlite={0}")
    public static Boolean[] databases() { return new Boolean[]{false, true}; }
    private final boolean sqlite;
    public KnowledgeServiceTest(boolean sqlite) { this.sqlite = sqlite; }
    private InMemoryZrLogDatabase database() throws Exception { return sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open(); }
    private KnowledgeService service(int user, Set<String> scopes) {
        return new KnowledgeService(() -> { try { return AccountAccess.load(user); } catch(Exception e) { throw new RuntimeException(e); } }, scopes, () -> "https://blog.example/sub");
    }
    private void seed(InMemoryZrLogDatabase db) throws Exception {
        db.execute("insert into user(userId,userName,role) values(?,?,?)", 2,"writer","author");
        for (int id=1; id<=6; id++) db.execute("insert into log(logId,userId,typeId,title,alias,digest,markdown,content,rubbish,privacy) values(?,?,?,?,?,?,?,?,?,?)",
                id, id<=3 ? 1 : 2, 1, "article " + id, "article-"+id, "<b>digest</b>", "body 100%_literal " + id, "<p>html</p>", id==2 || id==5, id==3 || id==6);
    }
    private SearchResult search(KnowledgeService service, String args) throws Exception { return (SearchResult) service.call("search_articles", JsonParser.parseString(args).getAsJsonObject()); }
    private ArticleResult read(KnowledgeService service, int id) throws Exception { return (ArticleResult) service.call("read_article", JsonParser.parseString("{\"id\":"+id+"}").getAsJsonObject()); }
    @Test public void rolesAndScopesFilterBeforePaginationAndMatchRead() throws Exception {
        try (InMemoryZrLogDatabase db=database()) {
            seed(db);
            for (String role : List.of("owner","admin","editor","author","contributor")) {
                db.execute("update user set role=? where userId=1", role);
                for (boolean all : List.of(false,true)) for(boolean drafts:List.of(false,true)) for(boolean privateArticles:List.of(false,true)) {
                    Options options=new Options(); options.allArticles=all; options.drafts=drafts; options.privateArticles=privateArticles;
                    KnowledgeService service=service(1, options.scopes());
                    List<Long> ids=new ArrayList<>(); Integer offset=0;
                    while(offset!=null) { SearchResult page=search(service,"{\"limit\":1,\"offset\":"+offset+"}"); for(Source s:page.articles) ids.add(s.id); offset=page.nextOffset; }
                    AccountAccess account=AccountAccess.load(1);
                    for(int id=1;id<=6;id++) {
                        boolean expected=com.zrlog.data.security.ArticleAccess.canRead(account,options.scopes(),id<=3?1:2,id==2||id==5,id==3||id==6);
                        assertEquals(role+" id="+id,expected,ids.contains((long)id));
                        if(expected) assertEquals(id,read(service,id).source.id);
                        else { final int denied=id; assertEquals("Article unavailable",assertThrows(IllegalArgumentException.class,()->read(service,denied)).getMessage()); }
                    }
                }
            }
        }
    }
    @Test public void literalSearchBoundedInputsAndChunkedContent() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            seed(db); KnowledgeService service=service(1,Set.of("articles:read"));
            assertEquals(1,search(service,"{\"query\":\"100%_literal\"}").articles.size());
            assertEquals(0,search(service,"{\"query\":\"100%_missing\"}").articles.size());
            assertEquals(0,search(service,"{\"query\":\"' OR 1=1 --\"}").articles.size());
            assertEquals("digest",search(service,"{}").articles.get(0).excerpt);
            ArticleResult part=(ArticleResult)service.call("read_article",JsonParser.parseString("{\"id\":1,\"length\":4}").getAsJsonObject());
            assertEquals("body",part.content); assertEquals(Integer.valueOf(4),part.nextOffset);
            assertTrue(part.source.url.startsWith("https://blog.example/sub/"));
            for(String args:List.of("{\"limit\":11}","{\"limit\":1.1}","{\"limit\":\"1\"}","{\"offset\":-1}","{\"query\":null}","{\"sql\":\"select\"}")) assertThrows(IllegalArgumentException.class,()->search(service,args));
            assertEquals("Article unavailable",assertThrows(IllegalArgumentException.class,()->read(service,999)).getMessage());
            db.execute("update user set enabled=? where userId=1",false);
            assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class,()->search(service,"{}"));
        }
    }
}
