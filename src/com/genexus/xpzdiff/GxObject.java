package com.genexus.xpzdiff;

import java.util.*;

public class GxObject {
    private String name;
    private String type; // Procedure, WebPanel, Transaction, DataProvider, etc.
    private String description;
    private String guid;
    private Map<String, GxPart> parts = new LinkedHashMap<>();

    public GxObject(String name, String type, String description, String guid) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.guid = guid;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public String getGuid() { return guid; }

    public Map<String, GxPart> getParts() { return parts; }

    public void addPart(GxPart part) {
        parts.put(part.getCategory(), part);
    }

    public GxPart getPart(String category) {
        return parts.get(category);
    }
}
