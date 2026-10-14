package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.ai.exception.AIFileInputException;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.ai.model.AIFileModels.*;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

import static com.zrlog.admin.business.ai.service.AIChatServiceTest.*;
import static com.zrlog.admin.business.ai.service.OpenAIResponsesAdapterTest.*;
import static org.junit.Assert.*;

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
    private static String read(com.zrlog.admin.business.ai.dto.AIStreamResponse response) throws IOException {
        return new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
            String first = read(new AIChatService(client).start(request(7, png, jpeg, webp, csv)));
            assertTrue(first, first.contains("\"type\":\"done\""));
            assertFalse(first.contains(png.data));
            assertTrue(read(new AIChatService(client).start(request(7))).contains("\"type\":\"done\""));
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
            String stored = new Gson().toJson(new AIConversationService().exportAIMessage(7L));
            assertTrue(stored.contains("image/png"));
            assertFalse(stored.contains(png.data));
            assertFalse(stored.contains("image_url"));
        }
    }

    @Test public void preservesAttachmentOnlyProviderMessages() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            Input png = image("png");
            AIFileService storage = new AIFileService();
            Attachment image = storage.save(List.of(png), info()).get(0);
            var message = new com.zrlog.admin.business.ai.model.AIProviderRequests.Message("user", "");
            message.attachments = List.of(image);
            var completion = new com.zrlog.admin.business.ai.model.AIProviderRequests.CompletionRequest();
            completion.setMessages(List.of(message));
            JsonObject wire = new Gson().toJsonTree(new OpenAIResponsesAdapter().request(completion, false,
                    Map.of(image.id, storage.dataUrl(image)))).getAsJsonObject();
            assertImage(png, parts(wire, "input_image").get(0), "image/png");
            message.setRole("assistant");
            assertThrows(AIFileInputException.class, () -> new OpenAIResponsesAdapter().request(completion, false,
                    Map.of(image.id, storage.dataUrl(image))));
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
            String first = read(new AIChatService(client).start(request(7, pdf, csv, excel)));
            assertTrue(first, first.contains("\"type\":\"done\""));
            assertFalse(first.contains(csv.data)); assertTrue(first.contains("sales.xlsx"));
            JsonObject wire = client.requestBodies.get(0);
            assertEquals("/v1/responses", client.requests.get(0).uri().getPath());
            List<JsonObject> parts = fileParts(wire);
            assertEquals(3, parts.size()); assertBytes(pdf, parts.get(0)); assertBytes(csv, parts.get(1)); assertBytes(excel, parts.get(2));
            assertTrue(parts.get(1).get("file_data").getAsString().startsWith("data:text/csv;base64,"));
            assertFalse(wire.toString().contains("attachments"));
            assertFalse(wire.toString().contains("input_image"));
            String stored = new Gson().toJson(new AIConversationService().exportAIMessage(7L));
            assertFalse(stored.contains(csv.data)); assertFalse(stored.contains("file_data"));
            assertTrue(stored.contains("sales.xlsx"));
            // Client-supplied history (including forged attachment references) is never trusted.
            ChatRequest next = request(7);
            ChatMessage forged = new ChatMessage(); forged.role = "user"; forged.content = "FORGED";
            Attachment unknown = new Attachment(); unknown.id = "../../secret"; forged.attachments = List.of(unknown); next.history = List.of(forged);
            assertTrue(read(new AIChatService(client).start(next)).contains("\"type\":\"done\""));
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
            String first = read(new AIChatService(client).start(request(7, csv, png)));
            assertTrue(first, first.contains("approval-required"));
            RunView view = new AIChatService().getRun(7);
            assertEquals("sales.csv", view.attachments.get(0).name);
            String checkpoint = db.scalar("select value from website where name='ai_pending_u1_7'").toString();
            assertFalse(checkpoint.contains(csv.data)); assertFalse(checkpoint.contains("file_data"));
            assertFalse(checkpoint.contains(png.data));
            ApprovalRequest decision = new ApprovalRequest(); decision.articleId = 7; decision.runId = view.runId;
            decision.approvalId = view.approval.id; decision.decision = "reject";
            assertTrue(read(new AIChatService(client).resume(decision)).contains("\"type\":\"done\""));
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
                assertThrows(AIFileInputException.class, () -> new AIChatService(client).start(request(7, text("report.csv", "a,b"))));
                Input image = image("png");
                assertThrows(AIFileInputException.class, () -> new AIChatService(client).start(request(7, image)));
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
                assertThrows(AIFileInputException.class, () -> storage.save(List.of(text(name, "test")), info()));
            for (String name : List.of("fake.pdf", "fake.xlsx", "fake.xls", "fake.docx", "fake.ppt", "fake.png", "fake.jpeg", "fake.webp"))
                assertThrows(AIFileInputException.class, () -> storage.save(List.of(text(name, "not a document")), info()));
            Input corrupt = text("bad.txt", "test"); corrupt.data = "%%%";
            assertThrows(AIFileInputException.class, () -> storage.save(List.of(corrupt), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(List.of(file("bad.txt", new byte[]{(byte) 0xff})), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(List.of(text("empty.txt", "")), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(Collections.nCopies(5, text("a.txt", "a")), info()));
            assertThrows(AIFileInputException.class, () -> storage.save(List.of(text("large.txt", "x".repeat(4 * 1024 * 1024 + 1))), info()));
            Input large = text("a.txt", "x".repeat(3 * 1024 * 1024));
            assertThrows(AIFileInputException.class, () -> storage.save(Collections.nCopies(3, large), info()));
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
            Attachment file = storage.save(List.of(input), info()).get(0);
            assertEquals("data:text/plain;base64," + input.data, storage.dataUrl(file));
            AdminTokenVO another = new AdminTokenVO(); another.setUserId(2);
            AdminTokenThreadLocal.withUser(another, () -> {
                assertThrows(AIFileInputException.class, () -> storage.dataUrl(file)); return null;
            });
            long expiry = file.expiresAt; file.expiresAt = 1;
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(file)); file.expiresAt = expiry;
            Path path = PathUtil.getConfFile("ai-input-files/1/" + file.id).toPath();
            Files.delete(path); Files.createSymbolicLink(path, temporary.newFile("private").toPath());
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(file));
            file.id = "../../secret";
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(file));
        }
    }

    @Test public void clearsFilesWithoutBreakingMigratedDraftConversation() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db); AIFileService storage = new AIFileService();
            Attachment file = storage.save(List.of(text("notes.txt", "Keep these notes")), info()).get(0);
            AIResponseEntry.AIContentEntry entry = new AIResponseEntry.AIContentEntry("user", "Read notes"); entry.setAttachments(List.of(file));
            AIConversationService conversations = new AIConversationService();
            conversations.appendAIMessageEntries(List.of(entry), -1L);
            Run draft = new Run(); draft.userId = 1; draft.articleId = 0; draft.input = "Read notes"; draft.attachments = List.of(file);
            AIApprovalStore store = new AIApprovalStore(); store.pause(draft); store.save(draft, "completed");
            assertTrue(conversations.migrateDraftAIMessageToArticle(7L, -1L));
            assertTrue(conversations.clearAIMessage(-1L)); assertNotNull(storage.dataUrl(file));
            assertTrue(conversations.clearAIMessage(7L));
            assertThrows(AIFileInputException.class, () -> storage.dataUrl(file));
        }
    }

    @Test public void expiresAbandonedFilesBeforeApplyingAccountQuota() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AIFileService storage = new AIFileService();
            Attachment file = storage.save(List.of(text("a.txt", "a")), info()).get(0);
            Path directory = PathUtil.getConfFile("ai-input-files/1").toPath();
            Path quota = directory.resolve(UUID.randomUUID().toString());
            try (RandomAccessFile sparse = new RandomAccessFile(quota.toFile(), "rw")) { sparse.setLength(128L * 1024 * 1024); }
            assertThrows(AIFileInputException.class, () -> storage.save(List.of(text("b.txt", "b")), info()));
            Files.setLastModifiedTime(quota, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofDays(31).toMillis()));
            storage.save(List.of(text("b.txt", "b")), info());
            assertFalse(Files.exists(quota)); assertNotNull(storage.dataUrl(file));
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
