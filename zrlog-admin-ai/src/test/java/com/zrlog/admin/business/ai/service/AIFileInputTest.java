package com.zrlog.admin.business.ai.service;

import static com.zrlog.admin.business.ai.service.AIChatServiceTest.*;
import static com.zrlog.admin.business.ai.service.OpenAIResponsesAdapterTest.*;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIFileInputException;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.ai.model.AIFileModels.*;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.model.WebSite;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

public class AIFileInputTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private String previousRoot;
    @Before public void isolateFiles() throws Exception {
        previousRoot = PathUtil.getRootPath();
        PathUtil.setRootPath(temporary.newFolder("runtime").getAbsolutePath());
    }
    @After public void restoreFiles() { PathUtil.setRootPath(previousRoot); }

    private static Input file(String name, byte[] bytes) {
        Input input = new Input(); input.name = name; input.data = Base64.getEncoder().encodeToString(bytes); return input;
    }
    private static Input text(String name, String content) { return file(name, content.getBytes(StandardCharsets.UTF_8)); }
    private static AIWebSiteInfo info() {
        AIWebSiteInfo info = new AIWebSiteInfo(); info.setAi_provider(AIProviderType.OPEN_AI); info.setAi_model("gpt-4.1"); return info;
    }
    private static ChatRequest request(long articleId, Input... files) {
        ChatRequest input = new ChatRequest(); input.articleId = articleId; input.input = "Analyze the supplied document";
        input.files = List.of(files); return input;
    }
    private static void configure(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "gpt-4.1");
        db.putWebsite("ai_api_key", "test-key"); db.putWebsite("ai_base_url", "https://provider.example/v1");
        db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)", 7,1,1,"Before",true,false,2);
        db.execute("update user set preferences=? where userId=1", "{\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
    }

    private static String read(AIStreamResponse response) throws IOException {
        return new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test public void keepsTextOnlyConversationsWorkingWithRelativeConfigurationDirectory() throws Exception {
        String previousConf = System.getProperty("sws.conf.path");
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            Path conf = temporary.newFolder("text-conf").toPath();
            System.setProperty("sws.conf.path", Path.of("").toAbsolutePath().relativize(conf).toString());
            for (AIProviderType provider : AIProviderType.values()) {
                db.putWebsite("ai_provider", provider.name());
                String response = provider == AIProviderType.OPEN_AI ? terminal(MESSAGE)
                        : "data: {\"choices\":[{\"delta\":{\"content\":\"Answer\"}}]}\n\n"
                        + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
                FakeHttpClient client = new FakeHttpClient(streamResponse(response), streamResponse(response));
                for (String json : List.of("{\"input\":\"Hello\",\"articleId\":7}",
                        "{\"input\":\"Continue\",\"articleId\":7,\"files\":[]}")) {
                    String payload =
                            read(
                                    new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource())
                                                            .permissions(),
                        AccountServicesFixture.component(db.dataSource())
                                                            .identities(),
                        AccountServicesFixture.component(db.dataSource())
                                                            .preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                            ContentToolsFixture.from(db.config()), db.dataSource()))
                                            .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), new Gson().fromJson(json, ChatRequest.class), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
                    assertTrue(payload, payload.contains("\"type\":\"done\""));
                }
                assertEquals(2, client.requests.size());
            }
            assertFalse(Files.exists(conf.resolve("ai-input-files")));
        } finally {
            if (previousConf == null) System.clearProperty("sws.conf.path");
            else System.setProperty("sws.conf.path", previousConf);
        }
    }

    @Test public void storesAttachmentsUnderTheConfiguredRelativeDirectory() throws Exception {
        String previousConf = System.getProperty("sws.conf.path");
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            Path conf = temporary.newFolder("file-conf").toPath();
            System.setProperty("sws.conf.path", Path.of("").toAbsolutePath().relativize(conf).toString());
            AIFileService storage = new AIFileService();
            Input input = text("notes.txt", "Original document bytes");
            Attachment file = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(input), info()).get(0);
            assertArrayEquals(Base64.getDecoder().decode(input.data),
                    Files.readAllBytes(conf.resolve("ai-input-files/1/" + file.id)));
            assertEquals("data:text/plain;base64," + input.data, storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
            storage.remove(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(file));
            assertFalse(Files.exists(conf.resolve("ai-input-files/1/" + file.id)));
        } finally {
            if (previousConf == null) System.clearProperty("sws.conf.path");
            else System.setProperty("sws.conf.path", previousConf);
        }
    }

    private static List<JsonObject> fileParts(JsonObject body) {
        return parts(body, "input_file");
    }
    private static List<JsonObject> parts(JsonObject body, String type) {
        List<JsonObject> parts = new ArrayList<>();
        for (var item : body.getAsJsonArray("input")) {
            JsonArray content = item.getAsJsonObject().getAsJsonArray("content");
            if (content != null) for (var part : content) if (type.equals(part.getAsJsonObject().get("type").getAsString())) parts.add(part.getAsJsonObject());
        }
        return parts;
    }

    private static Input image(String extension) throws IOException {
        try (InputStream stream = AIFileInputTest.class.getResourceAsStream("/ai-input/sample." + extension)) {
            return file("sample." + extension, Objects.requireNonNull(stream).readAllBytes());
        }
    }

    private static void assertImage(Input original, JsonObject part, String mime) {
        assertEquals("input_image", part.get("type").getAsString());
        assertEquals("data:" + mime + ";base64," + original.data, part.get("image_url").getAsString());
        assertEquals("auto", part.get("detail").getAsString());
        assertFalse(part.has("file_data"));
        assertFalse(part.has("filename"));
    }

    @Test public void sendsImagesAndDocumentsAsDistinctInputsAndRestoresImagesOnFollowup() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            Input png = image("png"), jpeg = image("jpg"), webp = image("webp"), csv = text("notes.csv", "a,b\n1,2");
            jpeg.name = "Photo.JPEG";
            FakeHttpClient client = new FakeHttpClient(streamResponse(terminal(MESSAGE)), streamResponse(terminal(MESSAGE)));
            String first =
                    read(
                            new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7, png, jpeg, webp, csv), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
            assertTrue(first, first.contains("\"type\":\"done\""));
            assertFalse(first.contains(png.data));
            assertTrue(
                    read(new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()))
                            .contains("\"type\":\"done\""));
            for (JsonObject wire : client.requestBodies) {
                List<JsonObject> images = parts(wire, "input_image");
                assertEquals(3, images.size());
                assertImage(png, images.get(0), "image/png");
                assertImage(jpeg, images.get(1), "image/jpeg");
                assertImage(webp, images.get(2), "image/webp");
                assertEquals(1, fileParts(wire).size());
                assertBytes(csv, fileParts(wire).get(0));
                assertFalse(wire.toString().contains("attachments"));
            }
            String stored =
                    new Gson()
                            .toJson(
                                    new AIConversationService(
                                                    AccountServicesFixture.component(db.dataSource()).security(),
                                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                                    () -> new WebSite(db.dataSource()),
                                                    new AIFileService(),
                                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                                            .exportAIMessage(7L));
            assertTrue(stored.contains("image/png"));
            assertFalse(stored.contains(png.data));
            assertFalse(stored.contains("image_url"));
        }
    }

    @Test public void preservesAttachmentOnlyProviderMessages() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            Input png = image("png");
            AIFileService storage = new AIFileService();
            Attachment image = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(png), info()).get(0);
            var message = new AIProviderRequests.Message("user", "");
            message.attachments = List.of(image);
            var completion = new AIProviderRequests.CompletionRequest();
            completion.setMessages(List.of(message));
            JsonObject wire = new Gson().toJsonTree(new OpenAIResponsesAdapter().request(completion, false,
                    Map.of(image.id, storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), image)))).getAsJsonObject();
            assertImage(png, parts(wire, "input_image").get(0), "image/png");
            message.setRole("assistant");
            assertThrows(AIFileInputException.class, () -> new OpenAIResponsesAdapter().request(completion, false,
                    Map.of(image.id, storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), image))));
        }
    }
    private static void assertBytes(Input original, JsonObject part) {
        assertEquals(original.name, part.get("filename").getAsString());
        String data = part.get("file_data").getAsString();
        assertTrue(data.startsWith("data:"));
        assertArrayEquals(Base64.getDecoder().decode(original.data), Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1)));
    }

    @Test public void sendsRealDocumentBytesAndRestoresFilesOnFollowupWithoutExposingBase64() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            Input pdf = file("notes.pdf", pdf());
            Input csv = text("sales.csv", "region,amount\nShanghai,1537\nBeijing,2219\n");
            Input excel = file("sales.xlsx", workbook());
            FakeHttpClient client = new FakeHttpClient(streamResponse(terminal(MESSAGE)), streamResponse(terminal(MESSAGE)));
            String first =
                    read(
                            new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7, pdf, csv, excel), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
            assertTrue(first, first.contains("\"type\":\"done\""));
            assertFalse(first.contains(csv.data)); assertTrue(first.contains("sales.xlsx"));
            JsonObject wire = client.requestBodies.get(0);
            assertEquals("/v1/responses", client.requests.get(0).uri().getPath());
            List<JsonObject> parts = fileParts(wire);
            assertEquals(3, parts.size()); assertBytes(pdf, parts.get(0)); assertBytes(csv, parts.get(1)); assertBytes(excel, parts.get(2));
            assertTrue(parts.get(1).get("file_data").getAsString().startsWith("data:text/csv;base64,"));
            assertFalse(wire.toString().contains("attachments"));
            assertFalse(wire.toString().contains("input_image"));
            String stored =
                    new Gson()
                            .toJson(
                                    new AIConversationService(
                                                    AccountServicesFixture.component(db.dataSource()).security(),
                                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                                    () -> new WebSite(db.dataSource()),
                                                    new AIFileService(),
                                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                                            .exportAIMessage(7L));
            assertFalse(stored.contains(csv.data)); assertFalse(stored.contains("file_data"));
            assertTrue(stored.contains("sales.xlsx"));
            // Client-supplied history (including forged attachment references) is never trusted.
            ChatRequest next = request(7);
            ChatMessage forged = new ChatMessage(); forged.role = "user"; forged.content = "FORGED";
            Attachment unknown = new Attachment(); unknown.id = "../../secret"; forged.attachments = List.of(unknown); next.history = List.of(forged);
            assertTrue(
                    read(new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), next, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()))
                            .contains("\"type\":\"done\""));
            assertEquals(3, fileParts(client.requestBodies.get(1)).size());
            assertBytes(excel, fileParts(client.requestBodies.get(1)).get(2));
            assertFalse(client.requestBodies.get(1).toString().contains("FORGED"));
        }
    }

    @Test public void restoresActualFileContentAfterApprovalAndKeepsCheckpointSmall() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            Input csv = text("sales.csv", "month,revenue\nOctober,47925\n");
            Input png = image("png");
            JsonObject call = JsonParser.parseString(CALL).getAsJsonObject(); call.addProperty("name", "update_article");
            call.addProperty("arguments", "{\"id\":7,\"version\":2,\"title\":\"Revenue report\"}");
            FakeHttpClient client = new FakeHttpClient(streamResponse(terminal(call.toString())), streamResponse(terminal(MESSAGE)));
            String first =
                    read(
                            new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7, csv, png), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
            assertTrue(first, first.contains("approval-required"));
            RunView view =
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()))
                            .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7);
            assertEquals("sales.csv", view.attachments.get(0).name);
            String checkpoint = db.scalar("select value from website where name='ai_pending_u1_7'").toString();
            assertFalse(checkpoint.contains(csv.data)); assertFalse(checkpoint.contains("file_data"));
            assertFalse(checkpoint.contains(png.data));
            ApprovalRequest decision = new ApprovalRequest(); decision.articleId = 7; decision.runId = view.runId;
            decision.approvalId = view.approval.id; decision.decision = "reject";
            assertTrue(
                    read(new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                    ContentToolsFixture.from(db.config()), db.dataSource()))
                                    .resume(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), decision, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()))
                            .contains("\"type\":\"done\""));
            assertBytes(csv, fileParts(client.requestBodies.get(1)).get(0));
            assertImage(png, parts(client.requestBodies.get(1), "input_image").get(0), "image/png");
        }
    }

    @Test public void rejectsUnsupportedProvidersAndInvalidImagesBeforePersistingOrCallingProvider() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            for (String provider : List.of("GOOGLE_GEMINI", "QWEN", "DEEP_SEEK")) {
                db.putWebsite("ai_provider", provider);
                FakeHttpClient client = new FakeHttpClient();
                assertThrows(
                        AIFileInputException.class,
                        () ->
                                new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                        ContentToolsFixture.from(db.config()), db.dataSource()))
                                        .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7, text("report.csv", "a,b")), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
                Input image = image("png");
                assertThrows(
                        AIFileInputException.class,
                        () ->
                                new AIChatService(client,
                        null,
                        ContentToolsFixture.from(db.config()),
                        AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(
                                                        ContentToolsFixture.from(db.config()), db.dataSource()))
                                        .start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), request(7, image), com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
                assertTrue(client.requests.isEmpty());
            }
            assertThrows(AIFileInputException.class, () -> AIFileService.validateInputs(List.of(text("photo.png", "image"))));
            assertFalse(Files.exists(PathUtil.getConfFile("ai-input-files").toPath()));
        }
    }

    @Test public void rejectsMalformedNamesDataSignaturesAndSizeBeforeWriting() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AIFileService storage = new AIFileService();
            for (String name : List.of("../notes.txt", "a/b.txt", "a\\b.txt", "a\ntxt", "notes.exe"))
                assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text(name, "test")), info()));
            for (String name : List.of("fake.pdf", "fake.xlsx", "fake.xls", "fake.docx", "fake.ppt", "fake.png", "fake.jpeg", "fake.webp"))
                assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text(name, "not a document")), info()));
            Input corrupt = text("bad.txt", "test"); corrupt.data = "%%%";
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(corrupt), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(file("bad.txt", new byte[]{(byte) 0xff})), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("empty.txt", "")), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), Collections.nCopies(5, text("a.txt", "a")), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("large.txt", "x".repeat(4 * 1024 * 1024 + 1))), info()));
            Input large = text("a.txt", "x".repeat(3 * 1024 * 1024));
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), Collections.nCopies(3, large), info()));
            assertFalse(Files.exists(PathUtil.getConfFile("ai-input-files").toPath()));
        }
    }

    @Test public void rejectsMismatchedAndTruncatedImageSignaturesAndUnsupportedImageFormats() throws Exception {
        for (String extension : List.of("png", "jpg", "webp")) {
            Input image = image(extension);
            byte[] bytes = Base64.getDecoder().decode(image.data);
            AIFileService.validateInputs(List.of(image));
            assertThrows(AIFileInputException.class, () -> AIFileService.validateInputs(List.of(file(image.name, Arrays.copyOf(bytes, 12)))));
            String wrongName = extension.equals("png") ? "wrong.jpg" : "wrong.png";
            assertThrows(AIFileInputException.class, () -> AIFileService.validateInputs(List.of(file(wrongName, bytes))));
        }
        for (String name : List.of("image.svg", "image.gif", "image.heic"))
            assertThrows(AIFileInputException.class, () -> AIFileService.validateInputs(List.of(text(name, "<svg/>"))));
        assertTrue(AIFileService.capabilities(info()).extensions.containsAll(List.of(".png", ".jpg", ".jpeg", ".webp")));
        AIWebSiteInfo unsupported = info(); unsupported.setAi_model("gpt-3.5-turbo");
        assertTrue(AIFileService.capabilities(unsupported).extensions.isEmpty());
    }

    @Test public void isolatesAccountsAndRejectsExpiredOrSymlinkReferences() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AIFileService storage = new AIFileService(); Input input = text("private.txt", "private contents");
            Attachment file = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(input), info()).get(0);
            assertEquals("data:text/plain;base64," + input.data, storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
            AdminTokenVO another = new AdminTokenVO(); another.setUserId(2);
            AdminTokenThreadLocal.withUser(another, () -> {
                assertThrows(AIFileInputException.class, () -> storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file)); return null;
            });
            long expiry = file.expiresAt; file.expiresAt = 1;
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file)); file.expiresAt = expiry;
            Path path = PathUtil.getConfFile("ai-input-files/1/" + file.id).toPath();
            Files.delete(path); Files.createSymbolicLink(path, temporary.newFile("private").toPath());
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
            file.id = "../../secret";
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
        }
    }

    @Test public void previewsOnlyImagesReferencedByTheAuthorizedAccountAndConversation() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            AIFileService storage = new AIFileService();
            Input image = image("png");
            Attachment png = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(image), info()).get(0);
            Attachment document = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("notes.txt", "private notes")), info()).get(0);
            AIResponseEntry.AIContentEntry message = new AIResponseEntry.AIContentEntry("user", "Look at this image");
            message.setAttachments(List.of(png, document));
            AIConversationService conversations =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(message), 7L);
            AIChatService chat =
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()));
            assertArrayEquals(Base64.getDecoder().decode(image.data), storage.read(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7, png.id)));
            assertThrows(AIFileInputException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 0, png.id));
            assertThrows(AIFileInputException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7, document.id));
            assertThrows(AIFileInputException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7, "../../secret"));
            assertThrows(ArgsException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), -1, png.id));
            db.execute("insert into user(userId,userName,role,authVersion) values(?,?,?,?)", 2, "another", "admin", 0);
            AdminTokenVO another = new AdminTokenVO(); another.setUserId(2); another.setAuthVersion(0);
            AdminTokenThreadLocal.withUser(another, () -> {
                assertThrows(AIFileInputException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7, png.id)); return null;
            });
            db.execute("update user set role='author' where userId=1");
            db.execute("update log set userId=2,privacy=true where logId=7");
            assertThrows(PermissionErrorException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7, png.id));
        }
    }

    @Test public void previewsDraftAndPendingImagesButRejectsClearedOrExpiredFiles() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            AIFileService storage = new AIFileService();
            Attachment image = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(image("webp")), info()).get(0);
            Run run = new Run(); run.userId = 1; run.articleId = 0; run.input = "Describe"; run.attachments = List.of(image);
            AIApprovalStore checkpoints =
                    new AIApprovalStore(AccountServicesFixture.component(db.dataSource()).security());
            checkpoints.pause(run);
            checkpoints.save(run, "running");
            AIChatService chat =
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()));
            assertEquals(image.id, chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 0, image.id).id);
            checkpoints.save(run, "completed");
            AIResponseEntry.AIContentEntry message = new AIResponseEntry.AIContentEntry("user", "Describe");
            image.expiresAt = 1; message.setAttachments(List.of(image));
            AIConversationService conversations =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(message), -1L);
            assertThrows(AIFileInputException.class, () -> storage.read(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 0, image.id)));
            assertTrue(conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(-1L));
            assertThrows(AIFileInputException.class, () -> chat.imageAttachment(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 0, image.id));
        }
    }

    @Test public void clearsFilesWithoutBreakingMigratedDraftConversation() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db); AIFileService storage = new AIFileService();
            Attachment file = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("notes.txt", "Keep these notes")), info()).get(0);
            AIResponseEntry.AIContentEntry entry = new AIResponseEntry.AIContentEntry("user", "Read notes"); entry.setAttachments(List.of(file));
            AIConversationService conversations =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(entry), -1L);
            Run draft = new Run(); draft.userId = 1; draft.articleId = 0; draft.input = "Read notes"; draft.attachments = List.of(file);
            AIApprovalStore store =
                    new AIApprovalStore(AccountServicesFixture.component(db.dataSource()).security());
            store.pause(draft);
            store.save(draft, "completed");
            assertTrue(conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).migrateDraftAIMessageToArticle(7L, -1L));
            assertTrue(conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(-1L)); assertNotNull(storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
            assertTrue(conversations.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(7L));
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
        }
    }

    @Test public void expiresAbandonedFilesBeforeApplyingAccountQuota() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AIFileService storage = new AIFileService();
            Attachment file = storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("a.txt", "a")), info()).get(0);
            Path directory = PathUtil.getConfFile("ai-input-files/1").toPath();
            Path quota = directory.resolve(UUID.randomUUID().toString());
            try (RandomAccessFile sparse = new RandomAccessFile(quota.toFile(), "rw")) { sparse.setLength(128L * 1024 * 1024); }
            assertThrows(AIFileInputException.class, () -> storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("b.txt", "b")), info()));
            Files.setLastModifiedTime(quota, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofDays(31).toMillis()));
            storage.save(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), List.of(text("b.txt", "b")), info());
            assertFalse(Files.exists(quota)); assertNotNull(storage.dataUrl(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), file));
        }
    }

    private static byte[] pdf() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        String content = "BT /F1 12 Tf 72 720 Td (Known private document payload: 47925) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(bytes.size());
            bytes.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
        }
        int xref = bytes.size();
        bytes.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) bytes.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        bytes.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    private static byte[] workbook() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entries.put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entries.put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Sales\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entries.put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entries.put("xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Revenue</t></is></c><c r=\"B1\"><v>47925</v></c></row></sheetData></worksheet>");
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
}
