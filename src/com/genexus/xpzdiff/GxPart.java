package com.genexus.xpzdiff;

import java.util.*;

public class GxPart {
    private String typeGuid;
    private String name;
    private String category; // Definition, Rules, Events, Documentation, Variables, Conditions
    private String content;

    public GxPart(String typeGuid, String name, String category, String content) {
        this.typeGuid = typeGuid;
        this.name = name;
        this.category = category;
        this.content = content != null ? content : "";
    }

    public String getTypeGuid() { return typeGuid; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
