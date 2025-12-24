package com.tal.pro.service.resume;

import java.util.Map;

public interface SectionExtractor<T> {
    T extract(String text, Map<String, String> sections);

    String getSectionName();
}
