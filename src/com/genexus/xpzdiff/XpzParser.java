package com.genexus.xpzdiff;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class XpzParser {

    public static Map<String, GxObject> parseXpz(InputStream inputStream, String fileName) throws Exception {
        Map<String, GxObject> objectsMap = new LinkedHashMap<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        byte[] fileData = baos.toByteArray();

        List<InputStream> xmlStreams = new ArrayList<>();

        if (fileData.length >= 2 && fileData[0] == 'P' && fileData[1] == 'K') {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileData));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    ByteArrayOutputStream entryBaos = new ByteArrayOutputStream();
                    while ((len = zis.read(buffer)) != -1) {
                        entryBaos.write(buffer, 0, len);
                    }
                    xmlStreams.add(new ByteArrayInputStream(entryBaos.toByteArray()));
                }
            }
        } else {
            xmlStreams.add(new ByteArrayInputStream(fileData));
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);

        for (InputStream xmlIs : xmlStreams) {
            Document doc = dbf.newDocumentBuilder().parse(xmlIs);
            NodeList objs = doc.getElementsByTagName("Object");

            for (int i = 0; i < objs.getLength(); i++) {
                Element objEl = (Element) objs.item(i);
                String name = objEl.getAttribute("name");
                if (name == null || name.trim().isEmpty()) {
                    name = objEl.getAttribute("fullyQualifiedName");
                }
                if (name == null || name.trim().isEmpty()) continue;

                String type = objEl.getAttribute("type");
                String desc = objEl.getAttribute("description");
                String guid = objEl.getAttribute("guid");

                GxObject gxObj = objectsMap.get(name);
                if (gxObj == null) {
                    gxObj = new GxObject(name, type, desc, guid);
                    objectsMap.put(name, gxObj);
                }

                parseObjectParts(objEl, gxObj);
            }
        }

        return objectsMap;
    }

    private static void parseObjectParts(Element objEl, GxObject gxObj) {
        NodeList parts = objEl.getElementsByTagName("Part");

        StringBuilder rulesBuilder = new StringBuilder();
        StringBuilder eventsBuilder = new StringBuilder();
        StringBuilder docBuilder = new StringBuilder();
        StringBuilder defBuilder = new StringBuilder();
        StringBuilder varsBuilder = new StringBuilder();

        for (int j = 0; j < parts.getLength(); j++) {
            Element partEl = (Element) parts.item(j);
            String pType = partEl.getAttribute("type").toLowerCase();

            String sourceText = getChildText(partEl, "Source");
            String helpText = getChildText(partEl, "Help");
            if (helpText.isEmpty()) helpText = getChildText(partEl, "Documentation");
            String innerHtml = getChildText(partEl, "InnerHtml");

            // Extract Historia from InnerHtml / Help if present
            String historiaXml = extractHistoriaFromText(innerHtml);
            if (historiaXml.isEmpty()) historiaXml = extractHistoriaFromText(helpText);
            if (!historiaXml.isEmpty()) {
                docBuilder.append(historiaXml).append("\n");
            }

            // 1. Rules: Check if source contains Parm( or parm( or GUID for rules
            if (sourceText.trim().toLowerCase().startsWith("parm(") || sourceText.trim().toLowerCase().startsWith("parm ") ||
                pType.contains("c414ed00-8cc4-4f44-8820") || pType.contains("c414ed00-8cc4-0000-0021") || 
                partEl.getElementsByTagName("Rules").getLength() > 0) {
                if (!sourceText.isEmpty()) rulesBuilder.append(sourceText.trim()).append("\n");
            }
            // 2. Events: GUID 2ad62dea-fa41-4d5f-bd6c, c44bd5ff-f918-415b, c414ed00-8cc4-0000-0022
            else if (pType.contains("2ad62dea-fa41-4d5f") || pType.contains("c44bd5ff-f918-415b") || 
                     pType.contains("c414ed00-8cc4-0000-0022") || partEl.getElementsByTagName("Events").getLength() > 0 ||
                     (sourceText.contains("Event ") || sourceText.contains("Sub ") || sourceText.contains("EndSub"))) {
                if (!sourceText.isEmpty()) eventsBuilder.append(sourceText.trim()).append("\n");
            }
            // 3. Documentation / Help
            else if (pType.contains("00000000-0000-0000-0008") || !helpText.isEmpty()) {
                String cleanHelp = filterDocText(helpText);
                if (!cleanHelp.isEmpty()) docBuilder.append(cleanHelp).append("\n");
            }
            // 4. Definition / Web Form / Layout / Procedure Source
            else if (pType.contains("d24a58ad-57ba-41b7") || pType.contains("f9d30353-f4fa-448d") || pType.contains("528d1c06-a9c2-420d")) {
                if (!sourceText.isEmpty()) {
                    defBuilder.append(normalizeDefinitionXml(sourceText)).append("\n");
                } else {
                    String xmlContent = formatInnerXml(partEl);
                    if (!xmlContent.isEmpty()) defBuilder.append(normalizeDefinitionXml(xmlContent)).append("\n");
                }
            }
            // 5. Variables
            else if (pType.contains("9b0a32a3") || pType.contains("9b0a20a3")) {
                String varTxt = extractVariablesSummary(partEl);
                if (!varTxt.isEmpty()) varsBuilder.append(varTxt).append("\n");
            }
            else {
                if (!sourceText.isEmpty()) {
                    if (sourceText.trim().toLowerCase().startsWith("parm(")) {
                        rulesBuilder.append(sourceText.trim()).append("\n");
                    } else if (sourceText.contains("Event ") || sourceText.contains("Sub ")) {
                        eventsBuilder.append(sourceText.trim()).append("\n");
                    } else {
                        defBuilder.append(formatXmlIfPossible(sourceText)).append("\n");
                    }
                }
            }
        }

        // Fallback for object-level Rules and Events
        NodeList rulesTags = objEl.getElementsByTagName("Rules");
        for (int k = 0; k < rulesTags.getLength(); k++) {
            rulesBuilder.append(rulesTags.item(k).getTextContent().trim()).append("\n");
        }

        gxObj.addPart(new GxPart("", "Definition", "Definition", cleanText(defBuilder.toString())));
        gxObj.addPart(new GxPart("", "Rules", "Rules", cleanText(rulesBuilder.toString())));
        gxObj.addPart(new GxPart("", "Events", "Events", cleanText(eventsBuilder.toString())));
        gxObj.addPart(new GxPart("", "Documentation", "Documentation", cleanText(docBuilder.toString())));
        if (varsBuilder.length() > 0) gxObj.addPart(new GxPart("", "Variables", "Variables", cleanText(varsBuilder.toString())));
    }

    public static String extractHistoriaFromText(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "";
        String decoded = decodeHtmlEntities(rawText);

        int start = decoded.toLowerCase().indexOf("<histor");
        if (start != -1) {
            int end = decoded.toLowerCase().indexOf("</histor", start);
            if (end != -1) {
                int endClose = decoded.indexOf(">", end);
                if (endClose != -1) {
                    String historiaStr = decoded.substring(start, endClose + 1);
                    return formatXmlIfPossible(cleanXmlTags(historiaStr));
                }
            } else {
                // Return from <Historia to end of string if not closed
                String historiaStr = decoded.substring(start);
                return formatXmlIfPossible(cleanXmlTags(historiaStr));
            }
        }
        return "";
    }

    private static String cleanXmlTags(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("(?i)<\\?xml:namespace[^>]*>", "")
                              .replaceAll("(?i)</?o:[^>]*>", "")
                              .replaceAll("(?i)<(span|p|div|font|o:p)[^>]*>", "")
                              .replaceAll("(?i)</(span|p|div|font|o:p)>", "")
                              .replaceAll("<\\?xml[^>]*\\?>", "")
                              .replaceAll("<!--[\\s\\S]*?-->", "")
                              .replaceAll("['\"]>&lt;", "<")
                              .replaceAll("&lt;", "<")
                              .replaceAll("&gt;", ">");
        return decodeHtmlEntities(cleaned).trim();
    }

    private static String filterDocText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        trimmed = trimmed.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-[A-Za-z0-9]+", "").trim();
        return trimmed;
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    public static String normalizeDefinitionXml(String xml) {
        if (xml == null || xml.isEmpty()) return "";
        String trimmed = xml.trim();
        // Remove outer <GxMultiForm> and <Form> tags if they wrap the HTML layout to align V1 and V2
        if (trimmed.contains("<GxMultiForm") && trimmed.contains("</GxMultiForm>")) {
            trimmed = trimmed.replaceAll("(?s)^<GxMultiForm[^>]*>\\s*", "")
                             .replaceAll("(?s)</GxMultiForm>\\s*$", "")
                             .replaceAll("(?s)^<Form[^>]*>\\s*", "")
                             .replaceAll("(?s)</Form>\\s*$", "");
        }
        return formatXmlIfPossible(trimmed);
    }

    public static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text.replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&nbsp;", " ");
    }

    public static String formatXmlIfPossible(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        String trimmed = input.trim();
        if (!trimmed.contains("<")) return trimmed;

        // Try standard DOM formatting
        try {
            int firstTag = trimmed.indexOf("<");
            if (firstTag > 0) trimmed = trimmed.substring(firstTag);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setNamespaceAware(false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(trimmed)));

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString().trim();
        } catch (Exception e) {
            return fallbackFormatXml(trimmed);
        }
    }

    private static String fallbackFormatXml(String xml) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        // Split before '<' or after '>'
        String[] tokens = xml.split("(?=<)|(?<=\\>)");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;
            if (token.startsWith("</")) {
                indent = Math.max(0, indent - 1);
                sb.append(getIndentSpaces(indent)).append(token).append("\n");
            } else if (token.startsWith("<") && !token.startsWith("<?") && !token.startsWith("<!") && !token.endsWith("/>") && !token.contains("</")) {
                sb.append(getIndentSpaces(indent)).append(token).append("\n");
                indent++;
            } else {
                sb.append(getIndentSpaces(indent)).append(token).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String getIndentSpaces(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) sb.append("  ");
        return sb.toString();
    }

    private static String getChildText(Element el, String tagName) {
        NodeList nl = el.getElementsByTagName(tagName);
        if (nl.getLength() > 0) {
            return nl.item(0).getTextContent().trim();
        }
        return "";
    }

    private static String extractVariablesSummary(Element partEl) {
        NodeList vars = partEl.getElementsByTagName("Variable");
        if (vars.getLength() == 0) return partEl.getTextContent().trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vars.getLength(); i++) {
            Element v = (Element) vars.item(i);
            String name = getChildText(v, "Name");
            if (name.isEmpty()) name = v.getAttribute("Name");
            String type = getChildText(v, "Type");
            if (!name.isEmpty()) {
                sb.append("&").append(name).append(" : ").append(type.isEmpty() ? "Var" : type).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String formatInnerXml(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                sb.append("<").append(c.getNodeName()).append(">");
                if (c.getChildNodes().getLength() == 1 && c.getFirstChild().getNodeType() == Node.TEXT_NODE) {
                    sb.append(c.getTextContent().trim());
                } else if (c.getChildNodes().getLength() > 0) {
                    sb.append("\n").append(formatInnerXml(c));
                }
                sb.append("</").append(c.getNodeName()).append(">\n");
            }
        }
        return sb.toString().trim();
    }
}
