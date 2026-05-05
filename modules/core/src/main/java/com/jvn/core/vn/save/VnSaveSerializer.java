package com.jvn.core.vn.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON-based serializer for VN save data.
 * Replaces Java object serialization with a human-readable, versionable format.
 */
public final class VnSaveSerializer {
    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_SCENARIO_ID = "scenarioId";
    private static final String KEY_NODE_INDEX = "nodeIndex";
    private static final String KEY_BACKGROUND_ID = "backgroundId";
    private static final String KEY_VARIABLES = "variables";
    private static final String KEY_READ_NODES = "readNodes";
    private static final String KEY_VISIBLE_CHARACTERS = "visibleCharacters";
    private static final String KEY_SKIP_MODE = "skipMode";
    private static final String KEY_AUTO_PLAY_MODE = "autoPlayMode";
    private static final String KEY_AUTO_PLAY_TIMER = "autoPlayTimer";
    private static final String KEY_UI_HIDDEN = "uiHidden";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_SAVE_TIMESTAMP = "saveTimestamp";
    private static final String KEY_SAVE_NAME = "saveName";
    private static final String KEY_CALL_STACK = "callStack";
    private static final String KEY_GLOBAL_POSITION_CHARACTERS = "globalPositionCharacters";
    private static final String KEY_CHARACTER_DEFINED_POSITIONS = "characterDefinedPositions";
    private static final String KEY_SCRIPT_NAME = "scriptName";
    private static final String KEY_RPG_STATE_SERIALIZED = "rpgStateSerialized";

    private VnSaveSerializer() {}

    /**
     * Serialize save data to JSON string.
     */
    public static String toJson(VnSaveData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        // Schema version
        appendField(sb, KEY_SCHEMA_VERSION, data.getSchemaVersion(), false);
        
        // Core state
        appendStringField(sb, KEY_SCENARIO_ID, data.getScenarioId());
        appendStringField(sb, KEY_SCRIPT_NAME, data.getScriptName());
        appendField(sb, KEY_NODE_INDEX, data.getCurrentNodeIndex(), false);
        appendStringField(sb, KEY_BACKGROUND_ID, data.getCurrentBackgroundId());
        
        // Variables (as JSON object)
        sb.append("  \"").append(KEY_VARIABLES).append("\": ");
        appendVariablesObject(sb, data.getVariables());
        sb.append(",\n");
        
        // Read nodes (as JSON array)
        sb.append("  \"").append(KEY_READ_NODES).append("\": ");
        appendIntArray(sb, data.getReadNodes());
        sb.append(",\n");
        
        // Visible characters
        sb.append("  \"").append(KEY_VISIBLE_CHARACTERS).append("\": ");
        appendCharactersObject(sb, data.getVisibleCharacters());
        sb.append(",\n");

        // CALL/RETURN stack and character global-position metadata
        sb.append("  \"").append(KEY_CALL_STACK).append("\": ");
        appendIntegerList(sb, data.getCallStack());
        sb.append(",\n");
        sb.append("  \"").append(KEY_GLOBAL_POSITION_CHARACTERS).append("\": ");
        appendStringArray(sb, data.getGlobalPositionCharacters());
        sb.append(",\n");
        sb.append("  \"").append(KEY_CHARACTER_DEFINED_POSITIONS).append("\": ");
        appendStringObject(sb, data.getCharacterDefinedPositions());
        sb.append(",\n");
        
        // Modes
        appendField(sb, KEY_SKIP_MODE, data.isSkipMode(), false);
        appendField(sb, KEY_AUTO_PLAY_MODE, data.isAutoPlayMode(), false);
        appendField(sb, KEY_AUTO_PLAY_TIMER, data.getAutoPlayTimer(), false);
        appendField(sb, KEY_UI_HIDDEN, data.isUiHidden(), false);
        
        // Settings
        sb.append("  \"").append(KEY_SETTINGS).append("\": ");
        appendSettingsObject(sb, data.getSettings());
        sb.append(",\n");
        
        // Metadata
        appendField(sb, KEY_SAVE_TIMESTAMP, data.getSaveTimestamp(), false);
        appendStringField(sb, KEY_SAVE_NAME, data.getSaveName());
        appendStringField(sb, KEY_RPG_STATE_SERIALIZED, serializeToBase64(data.getRpgState()), true);
        
        sb.append("}");
        return sb.toString();
    }

    private static String toNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Deserialize save data from JSON string.
     */
    public static VnSaveData fromJson(String json) throws IOException {
        VnSaveData data = new VnSaveData();
        Map<String, Object> map = parseJsonObject(json);
        
        if (map.containsKey(KEY_SCHEMA_VERSION)) {
            data.setSchemaVersion(((Number) map.get(KEY_SCHEMA_VERSION)).intValue());
        }
        if (map.containsKey(KEY_SCENARIO_ID)) {
            data.setScenarioId((String) map.get(KEY_SCENARIO_ID));
        }
        if (map.containsKey(KEY_SCRIPT_NAME)) {
            data.setScriptName((String) map.get(KEY_SCRIPT_NAME));
        }
        if (map.containsKey(KEY_NODE_INDEX)) {
            data.setCurrentNodeIndex(((Number) map.get(KEY_NODE_INDEX)).intValue());
        }
        if (map.containsKey(KEY_BACKGROUND_ID)) {
            data.setCurrentBackgroundId((String) map.get(KEY_BACKGROUND_ID));
        }
        if (map.containsKey(KEY_VARIABLES)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vars = (Map<String, Object>) map.get(KEY_VARIABLES);
            data.setVariables(vars != null ? new HashMap<>(vars) : new HashMap<>());
        }
        if (map.containsKey(KEY_READ_NODES)) {
            @SuppressWarnings("unchecked")
            List<Number> readList = (List<Number>) map.get(KEY_READ_NODES);
            Set<Integer> readNodes = new HashSet<>();
            if (readList != null) {
                for (Number n : readList) {
                    readNodes.add(n.intValue());
                }
            }
            data.setReadNodes(readNodes);
        }
        if (map.containsKey(KEY_VISIBLE_CHARACTERS)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> charsMap = (Map<String, Object>) map.get(KEY_VISIBLE_CHARACTERS);
            Map<String, String[]> visibleCharacters = new HashMap<>();
            if (charsMap != null) {
                for (Map.Entry<String, Object> entry : charsMap.entrySet()) {
                    @SuppressWarnings("unchecked")
                    List<Object> arr = (List<Object>) entry.getValue();
                    if (arr != null && arr.size() >= 2) {
                        String charId = toNullableString(arr.get(0));
                        String expression = toNullableString(arr.get(1));
                        String layer = arr.size() >= 3 ? toNullableString(arr.get(2)) : null;
                        if (layer != null && !layer.isBlank()) {
                            visibleCharacters.put(entry.getKey(), new String[]{charId, expression, layer});
                        } else {
                            visibleCharacters.put(entry.getKey(), new String[]{charId, expression});
                        }
                    }
                }
            }
            data.setVisibleCharacters(visibleCharacters);
        }
        if (map.containsKey(KEY_CALL_STACK)) {
            @SuppressWarnings("unchecked")
            List<Object> callList = (List<Object>) map.get(KEY_CALL_STACK);
            List<Integer> callStack = new ArrayList<>();
            if (callList != null) {
                for (Object value : callList) {
                    if (value instanceof Number number) {
                        callStack.add(number.intValue());
                    } else if (value != null) {
                        try {
                            callStack.add(Integer.parseInt(String.valueOf(value)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            data.setCallStack(callStack);
        }
        if (map.containsKey(KEY_GLOBAL_POSITION_CHARACTERS)) {
            @SuppressWarnings("unchecked")
            List<Object> globalCharsRaw = (List<Object>) map.get(KEY_GLOBAL_POSITION_CHARACTERS);
            Set<String> globalChars = new HashSet<>();
            if (globalCharsRaw != null) {
                for (Object value : globalCharsRaw) {
                    String id = toNullableString(value);
                    if (id != null && !id.isBlank()) globalChars.add(id);
                }
            }
            data.setGlobalPositionCharacters(globalChars);
        }
        if (map.containsKey(KEY_CHARACTER_DEFINED_POSITIONS)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) map.get(KEY_CHARACTER_DEFINED_POSITIONS);
            Map<String, String> defined = new HashMap<>();
            if (raw != null) {
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                    String value = toNullableString(entry.getValue());
                    if (value != null && !value.isBlank()) {
                        defined.put(entry.getKey(), value);
                    }
                }
            }
            data.setCharacterDefinedPositions(defined);
        }
        if (map.containsKey(KEY_SKIP_MODE)) {
            data.setSkipMode(Boolean.TRUE.equals(map.get(KEY_SKIP_MODE)));
        }
        if (map.containsKey(KEY_AUTO_PLAY_MODE)) {
            data.setAutoPlayMode(Boolean.TRUE.equals(map.get(KEY_AUTO_PLAY_MODE)));
        }
        if (map.containsKey(KEY_AUTO_PLAY_TIMER)) {
            data.setAutoPlayTimer(((Number) map.get(KEY_AUTO_PLAY_TIMER)).longValue());
        }
        if (map.containsKey(KEY_UI_HIDDEN)) {
            data.setUiHidden(Boolean.TRUE.equals(map.get(KEY_UI_HIDDEN)));
        }
        if (map.containsKey(KEY_SETTINGS)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> settingsMap = (Map<String, Object>) map.get(KEY_SETTINGS);
            data.setSettings(parseSettings(settingsMap));
        }
        if (map.containsKey(KEY_SAVE_TIMESTAMP)) {
            data.setSaveTimestamp(((Number) map.get(KEY_SAVE_TIMESTAMP)).longValue());
        }
        if (map.containsKey(KEY_SAVE_NAME)) {
            data.setSaveName((String) map.get(KEY_SAVE_NAME));
        }
        if (map.containsKey(KEY_RPG_STATE_SERIALIZED)) {
            data.setRpgState(deserializeFromBase64((String) map.get(KEY_RPG_STATE_SERIALIZED)));
        }
        
        return data;
    }

    /**
     * Write save data to file as JSON.
     */
    public static void writeToFile(VnSaveData data, Path path) throws IOException {
        String json = toJson(data);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * Read save data from JSON file.
     */
    public static VnSaveData readFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    // --- Helper methods for JSON generation ---

    private static void appendField(StringBuilder sb, String key, Object value, boolean last) {
        sb.append("  \"").append(key).append("\": ").append(value);
        if (!last) sb.append(",");
        sb.append("\n");
    }

    private static void appendStringField(StringBuilder sb, String key, String value) {
        appendStringField(sb, key, value, false);
    }

    private static void appendStringField(StringBuilder sb, String key, String value, boolean last) {
        sb.append("  \"").append(key).append("\": ");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
        if (!last) sb.append(",");
        sb.append("\n");
    }

    private static void appendVariablesObject(StringBuilder sb, Map<String, Object> variables) {
        sb.append("{");
        if (variables != null && !variables.isEmpty()) {
            sb.append("\n");
            int i = 0;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ");
                appendValue(sb, entry.getValue());
                if (i < variables.size() - 1) sb.append(",");
                sb.append("\n");
                i++;
            }
            sb.append("  ");
        }
        sb.append("}");
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    private static void appendIntArray(StringBuilder sb, Set<Integer> set) {
        sb.append("[");
        if (set != null && !set.isEmpty()) {
            int i = 0;
            for (Integer val : set) {
                sb.append(val);
                if (i < set.size() - 1) sb.append(", ");
                i++;
            }
        }
        sb.append("]");
    }

    private static void appendIntegerList(StringBuilder sb, List<Integer> values) {
        sb.append("[");
        if (values != null && !values.isEmpty()) {
            for (int i = 0; i < values.size(); i++) {
                sb.append(values.get(i));
                if (i < values.size() - 1) sb.append(", ");
            }
        }
        sb.append("]");
    }

    private static void appendStringArray(StringBuilder sb, Set<String> values) {
        sb.append("[");
        if (values != null && !values.isEmpty()) {
            int i = 0;
            for (String value : values) {
                if (value == null) {
                    sb.append("null");
                } else {
                    sb.append("\"").append(escapeJson(value)).append("\"");
                }
                if (i < values.size() - 1) sb.append(", ");
                i++;
            }
        }
        sb.append("]");
    }

    private static void appendStringObject(StringBuilder sb, Map<String, String> values) {
        sb.append("{");
        if (values != null && !values.isEmpty()) {
            sb.append("\n");
            int i = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ");
                if (entry.getValue() == null) {
                    sb.append("null");
                } else {
                    sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
                }
                if (i < values.size() - 1) sb.append(",");
                sb.append("\n");
                i++;
            }
            sb.append("  ");
        }
        sb.append("}");
    }

    private static void appendCharactersObject(StringBuilder sb, Map<String, String[]> characters) {
        sb.append("{");
        if (characters != null && !characters.isEmpty()) {
            sb.append("\n");
            int i = 0;
            for (Map.Entry<String, String[]> entry : characters.entrySet()) {
                String[] arr = entry.getValue();
                sb.append("    \"").append(escapeJson(entry.getKey())).append("\": [");
                if (arr != null && arr.length >= 2) {
                    sb.append("\"").append(escapeJson(arr[0])).append("\", ");
                    sb.append("\"").append(escapeJson(arr[1])).append("\"");
                    if (arr.length >= 3 && arr[2] != null) {
                        sb.append(", ");
                        sb.append("\"").append(escapeJson(arr[2])).append("\"");
                    }
                }
                sb.append("]");
                if (i < characters.size() - 1) sb.append(",");
                sb.append("\n");
                i++;
            }
            sb.append("  ");
        }
        sb.append("}");
    }

    private static void appendSettingsObject(StringBuilder sb, VnSaveData.SettingsData settings) {
        sb.append("{\n");
        if (settings != null) {
            sb.append("    \"textSpeed\": ").append(settings.getTextSpeed()).append(",\n");
            sb.append("    \"bgmVolume\": ").append(settings.getBgmVolume()).append(",\n");
            sb.append("    \"sfxVolume\": ").append(settings.getSfxVolume()).append(",\n");
            sb.append("    \"voiceVolume\": ").append(settings.getVoiceVolume()).append(",\n");
            sb.append("    \"autoPlayDelay\": ").append(settings.getAutoPlayDelay()).append(",\n");
            sb.append("    \"skipUnreadText\": ").append(settings.isSkipUnreadText()).append(",\n");
            sb.append("    \"skipAfterChoices\": ").append(settings.isSkipAfterChoices()).append(",\n");
            sb.append("    \"clickRevealBeforeAdvance\": ").append(settings.isClickRevealBeforeAdvance()).append(",\n");
            sb.append("    \"physicsFixedStepMs\": ").append(settings.getPhysicsFixedStepMs()).append(",\n");
            sb.append("    \"physicsMaxSubSteps\": ").append(settings.getPhysicsMaxSubSteps()).append(",\n");
            sb.append("    \"physicsDefaultFriction\": ").append(settings.getPhysicsDefaultFriction()).append(",\n");
            sb.append("    \"inputProfilePath\": ");
            if (settings.getInputProfilePath() == null) {
                sb.append("null,\n");
            } else {
                sb.append("\"").append(escapeJson(settings.getInputProfilePath())).append("\",\n");
            }
            sb.append("    \"inputProfileSerialized\": ");
            if (settings.getInputProfileSerialized() == null) {
                sb.append("null\n");
            } else {
                sb.append("\"").append(escapeJson(settings.getInputProfileSerialized())).append("\"\n");
            }
        }
        sb.append("  }");
    }

    private static VnSaveData.SettingsData parseSettings(Map<String, Object> map) {
        VnSaveData.SettingsData settings = new VnSaveData.SettingsData();
        if (map == null) return settings;
        
        if (map.containsKey("textSpeed")) {
            settings.setTextSpeed(((Number) map.get("textSpeed")).intValue());
        }
        if (map.containsKey("bgmVolume")) {
            settings.setBgmVolume(((Number) map.get("bgmVolume")).floatValue());
        }
        if (map.containsKey("sfxVolume")) {
            settings.setSfxVolume(((Number) map.get("sfxVolume")).floatValue());
        }
        if (map.containsKey("voiceVolume")) {
            settings.setVoiceVolume(((Number) map.get("voiceVolume")).floatValue());
        }
        if (map.containsKey("autoPlayDelay")) {
            settings.setAutoPlayDelay(((Number) map.get("autoPlayDelay")).longValue());
        }
        if (map.containsKey("skipUnreadText")) {
            settings.setSkipUnreadText(Boolean.TRUE.equals(map.get("skipUnreadText")));
        }
        if (map.containsKey("skipAfterChoices")) {
            settings.setSkipAfterChoices(Boolean.TRUE.equals(map.get("skipAfterChoices")));
        }
        if (map.containsKey("clickRevealBeforeAdvance")) {
            settings.setClickRevealBeforeAdvance(Boolean.TRUE.equals(map.get("clickRevealBeforeAdvance")));
        }
        if (map.containsKey("physicsFixedStepMs")) {
            settings.setPhysicsFixedStepMs(((Number) map.get("physicsFixedStepMs")).longValue());
        }
        if (map.containsKey("physicsMaxSubSteps")) {
            settings.setPhysicsMaxSubSteps(((Number) map.get("physicsMaxSubSteps")).intValue());
        }
        if (map.containsKey("physicsDefaultFriction")) {
            settings.setPhysicsDefaultFriction(((Number) map.get("physicsDefaultFriction")).doubleValue());
        }
        if (map.containsKey("inputProfilePath")) {
            settings.setInputProfilePath((String) map.get("inputProfilePath"));
        }
        if (map.containsKey("inputProfileSerialized")) {
            settings.setInputProfileSerialized((String) map.get("inputProfileSerialized"));
        }
        return settings;
    }

    private static String serializeToBase64(Object value) {
        if (!(value instanceof Serializable serializableValue)) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(serializableValue);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Object deserializeFromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return ois.readObject();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // --- Minimal JSON parser ---

    private static Map<String, Object> parseJsonObject(String json) throws IOException {
        return new JsonParser(json).parseObject();
    }

    private static class JsonParser {
        private final String json;
        private int pos;

        JsonParser(String json) {
            this.json = json.trim();
            this.pos = 0;
        }

        Map<String, Object> parseObject() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            
            if (peek() == '}') {
                pos++;
                return map;
            }
            
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                
                if (peek() == '}') {
                    pos++;
                    break;
                }
                expect(',');
            }
            
            return map;
        }

        List<Object> parseArray() throws IOException {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            
            if (peek() == ']') {
                pos++;
                return list;
            }
            
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                
                if (peek() == ']') {
                    pos++;
                    break;
                }
                expect(',');
            }
            
            return list;
        }

        Object parseValue() throws IOException {
            skipWhitespace();
            char c = peek();
            
            if (c == '"') return parseString();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            
            throw new IOException("Unexpected character: " + c + " at position " + pos);
        }

        String parseString() throws IOException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            
            while (pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= json.length()) throw new IOException("Unexpected end of string");
                    char escaped = json.charAt(pos++);
                    switch (escaped) {
                        case '"', '\\', '/' -> sb.append(escaped);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > json.length()) throw new IOException("Invalid unicode escape");
                            String hex = json.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IOException("Invalid escape: \\" + escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            
            throw new IOException("Unterminated string");
        }

        Number parseNumber() throws IOException {
            int start = pos;
            if (peek() == '-') pos++;
            
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            
            boolean isDouble = false;
            if (pos < json.length() && json.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                isDouble = true;
                pos++;
                if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            
            String numStr = json.substring(start, pos);
            return isDouble ? Double.parseDouble(numStr) : Long.parseLong(numStr);
        }

        Boolean parseBoolean() throws IOException {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IOException("Expected boolean at position " + pos);
        }

        Object parseNull() throws IOException {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IOException("Expected null at position " + pos);
        }

        void expect(char c) throws IOException {
            if (pos >= json.length() || json.charAt(pos) != c) {
                throw new IOException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        char peek() {
            return pos < json.length() ? json.charAt(pos) : 0;
        }

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }
}
