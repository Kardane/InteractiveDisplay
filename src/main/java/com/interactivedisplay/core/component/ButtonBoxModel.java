package com.interactivedisplay.core.component;

public final class ButtonBoxModel {
    public static final float TEXT_PIXEL_SCALE = 0.025f;
    public static final float TEXT_LINE_HEIGHT_PIXELS = 10.0f;
    private static final float MIN_FONT_SIZE = 0.1f;

    private ButtonBoxModel() {
    }

    public static ResolvedButtonBox resolve(ButtonComponentDefinition button) {
        float fontSize = normalizedFontSize(button);
        float horizontalPadding = button.padding().horizontal();
        float verticalPadding = button.padding().vertical();
        float intrinsicWidth = intrinsicLabelWidth(button.label(), fontSize);

        float width;
        float contentWidth;
        if (button.sizing().width() == ButtonSizeMode.CONTENT) {
            contentWidth = Math.max(intrinsicWidth, onePixel(fontSize));
            width = contentWidth + horizontalPadding * 2.0f;
        } else {
            width = button.size().width();
            contentWidth = Math.max(width - horizontalPadding * 2.0f, onePixel(fontSize));
        }

        int lineCount = button.sizing().width() == ButtonSizeMode.CONTENT
                ? explicitLineCount(button.label())
                : wrappedLineCount(button.label(), fontSize, contentWidth);
        float labelHeight = lineCount * lineHeight(fontSize);
        float labelWidth = Math.min(intrinsicWidth, contentWidth);

        float height;
        float contentHeight;
        if (button.sizing().height() == ButtonSizeMode.CONTENT) {
            contentHeight = labelHeight;
            height = labelHeight + verticalPadding * 2.0f;
        } else {
            height = button.size().height();
            contentHeight = Math.max(height - verticalPadding * 2.0f, onePixel(fontSize));
        }

        return new ResolvedButtonBox(
                width,
                height,
                contentWidth,
                contentHeight,
                labelWidth,
                labelHeight,
                lineCount
        );
    }

    public static int lineWidthPixels(ButtonComponentDefinition button) {
        ResolvedButtonBox box = resolve(button);
        return Math.max(1, Math.round(box.contentWidth() / (TEXT_PIXEL_SCALE * normalizedFontSize(button))));
    }

    public static float normalizedFontSize(ButtonComponentDefinition button) {
        return Math.max(button.fontSize(), MIN_FONT_SIZE);
    }

    private static float lineHeight(float fontSize) {
        return TEXT_LINE_HEIGHT_PIXELS * TEXT_PIXEL_SCALE * fontSize;
    }

    private static float onePixel(float fontSize) {
        return TEXT_PIXEL_SCALE * fontSize;
    }

    private static float intrinsicLabelWidth(String label, float fontSize) {
        if (label == null || label.isEmpty()) {
            return onePixel(fontSize);
        }
        float widest = 0.0f;
        for (String explicitLine : label.split("\\n", -1)) {
            widest = Math.max(widest, estimateTextUnits(explicitLine));
        }
        return Math.max(onePixel(fontSize), widest * TEXT_LINE_HEIGHT_PIXELS * TEXT_PIXEL_SCALE * fontSize);
    }

    private static int explicitLineCount(String label) {
        return label == null || label.isEmpty() ? 1 : label.split("\\n", -1).length;
    }

    private static int wrappedLineCount(String label, float fontSize, float availableWidth) {
        if (label == null || label.isEmpty()) {
            return 1;
        }
        int lines = 0;
        for (String explicitLine : label.split("\\n", -1)) {
            float width = estimateTextUnits(explicitLine) * TEXT_LINE_HEIGHT_PIXELS * TEXT_PIXEL_SCALE * fontSize;
            lines += Math.max(1, (int) Math.ceil(width / availableWidth));
        }
        return Math.max(1, lines);
    }

    private static float estimateTextUnits(String label) {
        if (label == null || label.isEmpty()) {
            return 1.0f;
        }
        float units = 0.0f;
        for (int index = 0; index < label.length();) {
            int codePoint = label.codePointAt(index);
            units += glyphUnit(codePoint);
            index += Character.charCount(codePoint);
        }
        return Math.max(1.0f, units);
    }

    private static float glyphUnit(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return 0.35f;
        }
        if (isAsciiLetterOrDigit(codePoint)) {
            return 0.62f;
        }
        if (isAsciiPunctuation(codePoint)) {
            return 0.5f;
        }
        if (isWideGlyph(codePoint)) {
            return 1.0f;
        }
        return 0.8f;
    }

    private static boolean isAsciiLetterOrDigit(int codePoint) {
        return codePoint <= 0x7F && Character.isLetterOrDigit(codePoint);
    }

    private static boolean isAsciiPunctuation(int codePoint) {
        return codePoint <= 0x7F && !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }

    private static boolean isWideGlyph(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || Character.getType(codePoint) == Character.OTHER_SYMBOL;
    }
}
