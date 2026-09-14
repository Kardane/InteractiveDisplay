package com.interactivedisplay.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class StyledTextSequenceTest {
    @Test
    void keepsEmojiZwJSequenceTogether() {
        StyledTextSequence sequence = StyledTextSequence.of(Component.literal("A👨‍👩‍👧‍👦B"));

        assertEquals(3, sequence.size());
        assertEquals("A", sequence.prefix(1).getString());
        assertEquals("A👨‍👩‍👧‍👦", sequence.prefix(2).getString());
        assertEquals("A👨‍👩‍👧‍👦B", sequence.prefix(3).getString());
    }

    @Test
    void preservesStyledRunsWhileRevealing() {
        Component source = Component.literal("안").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("녕").withStyle(ChatFormatting.BOLD));
        StyledTextSequence sequence = StyledTextSequence.of(source);

        Component prefix = sequence.prefix(2);
        assertEquals("안녕", prefix.getString());
        assertEquals(ChatFormatting.GOLD.getColor(), prefix.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(true, prefix.getSiblings().get(1).getStyle().isBold());
    }
}
