package cn.bugstack.ai.domain.agent.service.execute.auto.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses human-readable model output into stable SSE sections.
 */
public final class StageOutputParser {

    private StageOutputParser() {
    }

    public static List<Section> parse(String rawContent, String fallbackSection, Map<String, String> headingMappings) {
        List<Section> sections = new ArrayList<>();
        if (rawContent == null || rawContent.isBlank()) {
            return sections;
        }

        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();
        StringBuilder preamble = new StringBuilder();
        boolean insideControl = false;

        for (String originalLine : rawContent.split("\\R")) {
            String line = originalLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.contains("<CONTROL>")) {
                insideControl = !line.contains("</CONTROL>");
                continue;
            }
            if (insideControl) {
                if (line.contains("</CONTROL>")) {
                    insideControl = false;
                }
                continue;
            }

            String normalized = normalizeForMatching(line);
            Heading heading = findHeading(normalized, headingMappings);
            if (heading != null) {
                if (currentSection == null && !preamble.isEmpty()) {
                    addSection(sections, fallbackSection, preamble.toString());
                    preamble.setLength(0);
                } else {
                    addSection(sections, currentSection, currentContent.toString());
                    currentContent.setLength(0);
                }

                currentSection = heading.section();
                if (!heading.inlineContent().isBlank()) {
                    currentContent.append(heading.inlineContent());
                }
                continue;
            }

            if (currentSection == null) {
                appendLine(preamble, line);
            } else {
                appendLine(currentContent, line);
            }
        }

        if (currentSection == null) {
            addSection(sections, fallbackSection, preamble.toString());
        } else {
            addSection(sections, currentSection, currentContent.toString());
        }

        if (sections.isEmpty()) {
            addSection(sections, fallbackSection, rawContent);
        }
        return sections;
    }

    public static Map<String, String> mappings(String... aliasAndSectionPairs) {
        if (aliasAndSectionPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Heading mappings must be alias/section pairs");
        }
        Map<String, String> mappings = new LinkedHashMap<>();
        for (int i = 0; i < aliasAndSectionPairs.length; i += 2) {
            mappings.put(aliasAndSectionPairs[i], aliasAndSectionPairs[i + 1]);
        }
        return mappings;
    }

    public static String normalizeForMatching(String line) {
        if (line == null) {
            return "";
        }
        return line.trim()
                .replace('：', ':')
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("^[#>\\-\\s]+", "")
                .trim();
    }

    private static Heading findHeading(String normalizedLine, Map<String, String> headingMappings) {
        int colonIndex = normalizedLine.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }

        String headingText = normalizedLine.substring(0, colonIndex).trim();
        String inlineContent = normalizedLine.substring(colonIndex + 1).trim();
        for (Map.Entry<String, String> entry : headingMappings.entrySet()) {
            if (headingText.endsWith(entry.getKey())) {
                return new Heading(entry.getValue(), inlineContent);
            }
        }
        return null;
    }

    private static void addSection(List<Section> sections, String section, String content) {
        if (section == null || section.isBlank() || content == null || content.isBlank()) {
            return;
        }
        String normalizedContent = content.trim();
        if (!sections.isEmpty() && sections.get(sections.size() - 1).type().equals(section)) {
            Section previous = sections.remove(sections.size() - 1);
            sections.add(new Section(section, previous.content() + "\n" + normalizedContent));
        } else {
            sections.add(new Section(section, normalizedContent));
        }
    }

    private static void appendLine(StringBuilder content, String line) {
        if (!content.isEmpty()) {
            content.append('\n');
        }
        content.append(line);
    }

    public record Section(String type, String content) {
    }

    private record Heading(String section, String inlineContent) {
    }
}
