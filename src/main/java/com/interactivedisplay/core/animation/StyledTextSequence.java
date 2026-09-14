package com.interactivedisplay.core.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Flattens a Component into styled Unicode grapheme clusters so progressive reveal does not split
 * surrogate pairs, combining sequences, or emoji ZWJ sequences.
 */
public final class StyledTextSequence {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private final List<StyledGrapheme> graphemes;

    private StyledTextSequence(List<StyledGrapheme> graphemes) {
        this.graphemes = List.copyOf(graphemes);
    }

    public static StyledTextSequence of(Component component) {
        List<StyledGrapheme> graphemes = new ArrayList<>();
        component.<Void>visit((style, text) -> {
            Matcher matcher = GRAPHEME.matcher(text);
            while (matcher.find()) {
                graphemes.add(new StyledGrapheme(matcher.group(), style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledTextSequence(graphemes);
    }

    public int size() {
        return this.graphemes.size();
    }

    public Component prefix(int count) {
        int limit = Math.max(0, Math.min(count, this.graphemes.size()));
        MutableComponent result = Component.empty();
        if (limit == 0) {
            return result;
        }

        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (int index = 0; index < limit; index++) {
            StyledGrapheme grapheme = this.graphemes.get(index);
            if (runStyle != null && !runStyle.equals(grapheme.style())) {
                appendRun(result, run, runStyle);
                run.setLength(0);
            }
            runStyle = grapheme.style();
            run.append(grapheme.text());
        }
        if (!run.isEmpty()) {
            appendRun(result, run, runStyle == null ? Style.EMPTY : runStyle);
        }
        return result;
    }

    private static void appendRun(MutableComponent result, StringBuilder run, Style style) {
        result.append(Component.literal(run.toString()).setStyle(style));
    }

    private record StyledGrapheme(String text, Style style) {
    }
}
