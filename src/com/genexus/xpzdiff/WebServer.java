package com.genexus.xpzdiff;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebServer {

    private static Map<String, GxObject> objectsV1 = new LinkedHashMap<>();
    private static Map<String, GxObject> objectsV2 = new LinkedHashMap<>();
    private static String nameV1 = "Versión 1 (Base)";
    private static String nameV2 = "Versión 2 (Nueva)";

    public static int startServer(int preferredPort) throws Exception {
        int port = preferredPort;
        HttpServer server = null;

        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                break;
            } catch (IOException e) {
                port++;
            }
        }

        if (server == null) {
            throw new IOException("No se pudo asignar un puerto libre entre " + preferredPort + " y " + port);
        }

        server.createContext("/", new StaticHandler());
        server.createContext("/api/upload", new UploadHandler());
        server.createContext("/api/diff", new DiffHandler());
        server.createContext("/api/load-sample", new LoadSampleHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("=================================================");
        System.out.println(" Comparador de Archivos XPZ GeneXus 16 Iniciado ");
        System.out.println(" Servidor disponible en: http://localhost:" + port);
        System.out.println("=================================================");
        return port;
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            InputStream is = WebServer.class.getResourceAsStream("/web" + path);
            if (is == null) {
                // Try from local folder if running outside JAR
                File f = new File("web" + path);
                if (f.exists()) {
                    is = new FileInputStream(f);
                } else {
                    f = new File("c:/Ede/Ejemplos/Comparar XPZ/web" + path);
                    if (f.exists()) is = new FileInputStream(f);
                }
            }

            if (is == null) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            byte[] bytes = readAllBytes(is);
            String contentType = "text/html; charset=UTF-8";
            if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(455, -1);
                return;
            }

            try {
                InputStream is = exchange.getRequestBody();
                byte[] body = readAllBytes(is);

                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                String boundary = extractBoundary(contentType);

                if (boundary == null) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Formato multipart no válido\"}");
                    return;
                }

                Map<String, MultipartPart> parts = parseMultipart(body, boundary);

                MultipartPart file1 = parts.get("file1");
                MultipartPart file2 = parts.get("file2");

                if (file1 == null || file2 == null) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Debe seleccionar ambos archivos XPZ (Versión 1 y Versión 2)\"}");
                    return;
                }

                nameV1 = file1.filename != null ? file1.filename : "Archivo_V1.xpz";
                nameV2 = file2.filename != null ? file2.filename : "Archivo_V2.xpz";

                objectsV1 = XpzParser.parseXpz(new ByteArrayInputStream(file1.data), nameV1);
                objectsV2 = XpzParser.parseXpz(new ByteArrayInputStream(file2.data), nameV2);

                String jsonSummary = buildObjectsSummaryJson();
                sendJsonResponse(exchange, 200, jsonSummary);
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class LoadSampleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                File sample1 = new File("C:/Ede/Requerimientos/REQ26200-Cambio de Modelo y Versión- Pre-Desembolso (Validación y actualización)/XPZ/bk/HJMFW065.xpz");
                File sample2 = new File("C:/Ede/Requerimientos/REQ26200-Cambio de Modelo y Versión- Pre-Desembolso (Validación y actualización)/XPZ/HJMFW065.xpz");

                if (!sample1.exists() || !sample2.exists()) {
                    // Try another sample
                    sample1 = new File("C:/Ede/Requerimientos/REQ26086- Requerimiento Reportes/Versiones Clases/v1/RJMFA491.xpz");
                    sample2 = new File("C:/Ede/Requerimientos/REQ26086- Requerimiento Reportes/Versiones Clases/v2/RJMFA491.xpz");
                }

                if (!sample1.exists() || !sample2.exists()) {
                    sendJsonResponse(exchange, 404, "{\"error\":\"No se encontraron archivos de muestra en el sistema\"}");
                    return;
                }

                nameV1 = sample1.getName() + " (V1 Original)";
                nameV2 = sample2.getName() + " (V2 Modificado)";

                objectsV1 = XpzParser.parseXpz(new FileInputStream(sample1), nameV1);
                objectsV2 = XpzParser.parseXpz(new FileInputStream(sample2), nameV2);

                String jsonSummary = buildObjectsSummaryJson();
                sendJsonResponse(exchange, 200, jsonSummary);
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class DiffHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String objName = query.get("object");
            String partCategory = query.get("part");

            if (objName == null || partCategory == null) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Parámetros 'object' y 'part' requeridos\"}");
                return;
            }

            GxObject obj1 = objectsV1.get(objName);
            GxObject obj2 = objectsV2.get(objName);

            String text1 = (obj1 != null && obj1.getPart(partCategory) != null) ? obj1.getPart(partCategory).getContent() : "";
            String text2 = (obj2 != null && obj2.getPart(partCategory) != null) ? obj2.getPart(partCategory).getContent() : "";

            List<DiffEngine.DiffLine> diffs = DiffEngine.computeDiff(text1, text2);

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"object\": \"").append(escapeJson(objName)).append("\",\n");
            json.append("  \"part\": \"").append(escapeJson(partCategory)).append("\",\n");
            json.append("  \"nameV1\": \"").append(escapeJson(nameV1)).append("\",\n");
            json.append("  \"nameV2\": \"").append(escapeJson(nameV2)).append("\",\n");
            json.append("  \"lines\": [\n");

            int addedCount = 0, deletedCount = 0, modifiedCount = 0;

            for (int i = 0; i < diffs.size(); i++) {
                DiffEngine.DiffLine dl = diffs.get(i);
                if (dl.type == DiffEngine.ChangeType.ADDED) addedCount++;
                else if (dl.type == DiffEngine.ChangeType.DELETED) deletedCount++;
                else if (dl.type == DiffEngine.ChangeType.MODIFIED) modifiedCount++;

                json.append("    {");
                json.append("\"oldLine\": ").append(dl.oldLineNo).append(",");
                json.append("\"newLine\": ").append(dl.newLineNo).append(",");
                json.append("\"type\": \"").append(dl.type.name()).append("\",");
                json.append("\"oldContent\": ").append(dl.oldContent == null ? "null" : "\"" + escapeJson(dl.oldContent) + "\"").append(",");
                json.append("\"newContent\": ").append(dl.newContent == null ? "null" : "\"" + escapeJson(dl.newContent) + "\"");
                json.append("}");
                if (i < diffs.size() - 1) json.append(",");
                json.append("\n");
            }

            json.append("  ],\n");
            json.append("  \"stats\": {\n");
            json.append("    \"added\": ").append(addedCount).append(",\n");
            json.append("    \"deleted\": ").append(deletedCount).append(",\n");
            json.append("    \"modified\": ").append(modifiedCount).append(",\n");
            json.append("    \"totalLines\": ").append(diffs.size()).append("\n");
            json.append("  }\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    private static String buildObjectsSummaryJson() {
        Set<String> allObjNames = new LinkedHashSet<>();
        allObjNames.addAll(objectsV1.keySet());
        allObjNames.addAll(objectsV2.keySet());

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"nameV1\": \"").append(escapeJson(nameV1)).append("\",\n");
        sb.append("  \"nameV2\": \"").append(escapeJson(nameV2)).append("\",\n");
        sb.append("  \"objects\": [\n");

        List<String> list = new ArrayList<>(allObjNames);
        for (int i = 0; i < list.size(); i++) {
            String name = list.get(i);
            GxObject o1 = objectsV1.get(name);
            GxObject o2 = objectsV2.get(name);

            String status = "UNCHANGED";
            if (o1 == null) status = "ADDED";
            else if (o2 == null) status = "DELETED";
            else {
                // Check if any part differs
                boolean hasDiff = false;
                Set<String> categories = new LinkedHashSet<>();
                categories.addAll(o1.getParts().keySet());
                categories.addAll(o2.getParts().keySet());
                for (String cat : categories) {
                    String c1 = o1.getPart(cat) != null ? o1.getPart(cat).getContent() : "";
                    String c2 = o2.getPart(cat) != null ? o2.getPart(cat).getContent() : "";
                    if (!c1.equals(c2)) {
                        hasDiff = true;
                        break;
                    }
                }
                if (hasDiff) status = "MODIFIED";
            }

            String desc = o2 != null ? o2.getDescription() : (o1 != null ? o1.getDescription() : "");
            String type = o2 != null ? o2.getType() : (o1 != null ? o1.getType() : "");

            sb.append("    {");
            sb.append("\"name\": \"").append(escapeJson(name)).append("\",");
            sb.append("\"status\": \"").append(status).append("\",");
            sb.append("\"type\": \"").append(escapeJson(type)).append("\",");
            sb.append("\"description\": \"").append(escapeJson(desc)).append("\",");
            sb.append("\"partsV1\": ").append(getPartsJsonArray(o1)).append(",");
            sb.append("\"partsV2\": ").append(getPartsJsonArray(o2));
            sb.append("}");
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static String getPartsJsonArray(GxObject obj) {
        if (obj == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        List<String> keys = new ArrayList<>(obj.getParts().keySet());
        for (int i = 0; i < keys.size(); i++) {
            String cat = keys.get(i);
            sb.append("\"").append(escapeJson(cat)).append("\"");
            if (i < keys.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String param : contentType.split(";")) {
            param = param.trim();
            if (param.startsWith("boundary=")) {
                return param.substring(9).replace("\"", "");
            }
        }
        return null;
    }

    private static Map<String, MultipartPart> parseMultipart(byte[] body, String boundary) throws IOException {
        Map<String, MultipartPart> map = new HashMap<>();
        byte[] bBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        List<Integer> positions = findOccurrences(body, bBytes);
        for (int i = 0; i < positions.size() - 1; i++) {
            int start = positions.get(i) + bBytes.length;
            int end = positions.get(i + 1);

            // Strip leading CRLF if present
            if (start + 2 <= body.length && body[start] == '\r' && body[start + 1] == '\n') start += 2;
            if (end >= 2 && body[end - 2] == '\r' && body[end - 1] == '\n') end -= 2;

            if (start >= end) continue;

            // Find blank line dividing headers and body
            int headerEnd = -1;
            for (int k = start; k < end - 3; k++) {
                if (body[k] == '\r' && body[k+1] == '\n' && body[k+2] == '\r' && body[k+3] == '\n') {
                    headerEnd = k;
                    break;
                }
            }

            if (headerEnd != -1) {
                String headersStr = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
                byte[] partData = Arrays.copyOfRange(body, headerEnd + 4, end);

                String fieldName = extractHeaderParam(headersStr, "name");
                String fileName = extractHeaderParam(headersStr, "filename");

                if (fieldName != null) {
                    map.put(fieldName, new MultipartPart(fieldName, fileName, partData));
                }
            }
        }
        return map;
    }

    private static String extractHeaderParam(String header, String param) {
        int idx = header.indexOf(param + "=\"");
        if (idx != -1) {
            int start = idx + param.length() + 2;
            int end = header.indexOf("\"", start);
            if (end != -1) return header.substring(start, end);
        }
        return null;
    }

    private static List<Integer> findOccurrences(byte[] data, byte[] pattern) {
        List<Integer> res = new ArrayList<>();
        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) res.add(i);
        }
        return res;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf("=");
            if (eq > 0) {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    static class MultipartPart {
        String name;
        String filename;
        byte[] data;

        MultipartPart(String name, String filename, byte[] data) {
            this.name = name;
            this.filename = filename;
            this.data = data;
        }
    }
}
