package xyz.draba.spectate.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SpectatingScreenTest {
    @Test
    void spectatorViewOwnsBackgroundRendering() throws Exception {
        var method = SpectatingScreen.class.getDeclaredMethod(
                "extractBackground", GuiGraphicsExtractor.class,
                int.class, int.class, float.class);

        assertEquals(SpectatingScreen.class, method.getDeclaringClass());
    }
}
