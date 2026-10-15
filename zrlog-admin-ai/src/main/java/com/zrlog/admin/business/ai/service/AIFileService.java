package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.ai.model.AIFileModels.*;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.ai.exception.AIFileInputException;
import com.zrlog.admin.business.exception.AdminErrorCode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Private file inputs. No public upload URL, remote fetch or client-selected path. */
public final class AIFileService {
    public static final int MAX_REQUEST_BYTES = 12 * 1024 * 1024;
    public static final int MAX_CONTEXT_BYTES = 16 * 1024 * 1024;
    private static final long RETENTION = Duration.ofDays(30).toMillis();
    private static final long QUOTA = 128L * 1024 * 1024;
    private static final Object WRITE_LOCK = new Object();
    private static final Map<String, String> TYPES = types();
    private final Path root;

    // Runtime storage is a directory, not a bundled configuration resource.
    public AIFileService() { this(Path.of(PathUtil.getConfPath()).toAbsolutePath().normalize().resolve("ai-input-files")); }
    AIFileService(Path root) { this.root = root; }

    public static Capabilities capabilities(AIWebSiteInfo info) {
        Capabilities result = new Capabilities();
        String model = Objects.toString(info.getAi_model(), "").toLowerCase(Locale.ROOT);
        boolean openai = info.getAi_provider() == AIProviderType.OPEN_AI
                && model.matches("(?:gpt-(?:4o|4\\.1|5|6)(?:[.-].*)?|o3(?:-pro|-[0-9].*)?|o4-mini(?:-.*)?)")
                && !model.matches(".*(?:audio|realtime|transcrib|tts|image|search|codex).*");
        if (openai) result.extensions = new ArrayList<>(TYPES.keySet());
        return result;
    }

    public static void validateInputs(List<Input> inputs) {
        if (inputs == null || inputs.size() > 4) throw error("invalid");
        long total = 0;
        for (Input input : inputs) {
            if (input == null || input.name == null || input.name.isBlank() || input.name.length() > 255
                    || input.name.contains("/") || input.name.contains("\\") || input.name.codePoints().anyMatch(Character::isISOControl)
                    || input.data == null || input.data.isEmpty() || input.data.length() > ((4 * 1024 * 1024 + 2) / 3) * 4) throw error("invalid");
            String mime = TYPES.get(extension(input.name));
            if (mime == null) throw error("unsupported");
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(input.data); }
            catch (IllegalArgumentException e) { throw error("invalid"); }
            if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024 || (total += bytes.length) > 8 * 1024 * 1024) throw error("size");
            validateBytes(bytes, mime);
        }
    }

    public static void requireSupported(AIWebSiteInfo info, Collection<String> names) {
        List<String> supported = capabilities(info).extensions;
        for (String name : names) if (!supported.contains(extension(name))) throw error("unsupported");
    }

    public List<Attachment> save(List<Input> inputs, AIWebSiteInfo info) throws IOException {
        validateInputs(inputs);
        requireSupported(info, inputs.stream().map(file -> file.name).collect(Collectors.toList()));
        if (inputs.isEmpty()) return List.of();
        // The server runs in one process; concurrent article requests share the account quota.
        synchronized (WRITE_LOCK) { return write(inputs); }
    }

    private List<Attachment> write(List<Input> inputs) throws IOException {
        Path directory = directory();
        Files.createDirectories(directory);
        long used = 0, now = System.currentTimeMillis();
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.collect(Collectors.toList())) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.getLastModifiedTime(entry).toMillis() + RETENTION < now) Files.delete(entry);
                else used += Files.size(entry);
            }
        }
        long requested = inputs.stream().mapToLong(file -> Base64.getDecoder().decode(file.data).length).sum();
        if (used + requested > QUOTA) throw error("quota");
        List<Attachment> result = new ArrayList<>();
        try {
            for (Input input : inputs) {
                byte[] bytes = Base64.getDecoder().decode(input.data);
                Attachment file = new Attachment();
                file.id = UUID.randomUUID().toString(); file.name = input.name;
                file.mimeType = TYPES.get(extension(file.name)); file.size = bytes.length; file.expiresAt = now + RETENTION;
                Files.write(directory.resolve(file.id), bytes, StandardOpenOption.CREATE_NEW);
                result.add(file);
            }
            return result;
        } catch (IOException | RuntimeException e) { remove(result); throw e; }
    }

    public String dataUrl(Attachment file) throws IOException {
        Path path = path(file);
        if (file.expiresAt < System.currentTimeMillis() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || file.size <= 0 || file.size > 4 * 1024 * 1024 || Files.size(path) != file.size) throw error("unavailable");
        byte[] bytes;
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes(file.size + 1);
        }
        if (bytes.length != file.size || !Objects.equals(TYPES.get(extension(file.name)), file.mimeType)) throw error("unavailable");
        return "data:" + file.mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public void remove(Collection<Attachment> files) throws IOException {
        if (files == null) return;
        for (Attachment file : files) Files.deleteIfExists(path(file));
    }

    private Path directory() {
        Integer userId = AdminTokenThreadLocal.getUserId();
        if (userId == null || userId <= 0) throw new PermissionErrorException();
        Path directory = root.resolve(userId.toString());
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(directory)) throw new PermissionErrorException();
        return directory;
    }

    private Path path(Attachment file) {
        if (file == null || file.id == null || !file.id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw error("unavailable");
        return directory().resolve(file.id);
    }

    private static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static void validateBytes(byte[] bytes, String mime) {
        String prefix = new String(bytes, 0, Math.min(bytes.length, 12), StandardCharsets.ISO_8859_1);
        boolean valid;
        switch (mime) {
            case "image/png":
                valid = bytes.length >= 33 && prefix.startsWith("\u0089PNG\r\n\u001a\n")
                        && new String(bytes, 12, 4, StandardCharsets.US_ASCII).equals("IHDR");
                break;
            case "image/jpeg":
                valid = bytes.length >= 4 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                        && (bytes[2] & 0xff) == 0xff
                        && (bytes[bytes.length - 2] & 0xff) == 0xff && (bytes[bytes.length - 1] & 0xff) == 0xd9;
                break;
            case "image/webp":
                valid = bytes.length >= 20 && prefix.startsWith("RIFF") && prefix.endsWith("WEBP")
                        && Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()) == bytes.length - 8L
                        && List.of("VP8 ", "VP8L", "VP8X").contains(new String(bytes, 12, 4, StandardCharsets.US_ASCII));
                break;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": valid = prefix.startsWith("PK\u0003\u0004"); break;
            case "application/msword":
            case "application/vnd.ms-powerpoint":
            case "application/vnd.ms-excel": valid = prefix.startsWith("\u00d0\u00cf\u0011\u00e0\u00a1\u00b1\u001a\u00e1"); break;
            case "application/pdf": valid = prefix.startsWith("%PDF-"); break;
            default:
                try {
                    String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                    valid = text.indexOf('\0') < 0;
                } catch (CharacterCodingException e) { valid = false; }
        }
        if (!valid) throw error("invalid");
    }

    private static Map<String, String> types() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(".png", "image/png");
        result.put(".jpg", "image/jpeg");
        result.put(".jpeg", "image/jpeg");
        result.put(".webp", "image/webp");
        result.put(".pdf", "application/pdf");
        result.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        result.put(".doc", "application/msword");
        result.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        result.put(".ppt", "application/vnd.ms-powerpoint");
        result.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        result.put(".xls", "application/vnd.ms-excel");
        for (String extension : List.of(".txt", ".md", ".csv", ".tsv", ".json", ".html", ".xml", ".css", ".js", ".ts", ".py", ".java", ".c", ".cpp", ".h", ".go", ".rs", ".sh", ".yaml", ".yml")) result.put(extension, "text/plain");
        result.put(".csv", "text/csv"); result.put(".tsv", "text/tsv");
        result.put(".md", "text/markdown"); result.put(".json", "application/json");
        result.put(".html", "text/html"); result.put(".xml", "text/xml"); result.put(".css", "text/css");
        result.put(".js", "application/javascript"); result.put(".ts", "application/typescript");
        result.put(".py", "text/x-python"); result.put(".java", "text/x-java");
        result.put(".c", "text/x-c"); result.put(".cpp", "text/x-c++"); result.put(".h", "text/x-c");
        result.put(".go", "text/x-go"); result.put(".rs", "text/x-rust"); result.put(".sh", "text/x-shellscript");
        result.put(".yaml", "text/x-yaml"); result.put(".yml", "text/x-yaml");
        return Collections.unmodifiableMap(result);
    }

    public static AIFileInputException error(String key) {
        return new AIFileInputException(AdminErrorCode.valueOf("AI_FILE_" + key.toUpperCase(Locale.ROOT)));
    }
}
