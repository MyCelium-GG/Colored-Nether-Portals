package my.celium.org.colorednetherportals.client;

import my.celium.org.client.MycelTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A flat, wiki-themed button: one fill and a 1px border instead of
 * vanilla's bevelled sprite. The primary one (Join) is red. Keyboard focus
 * and narration work like any vanilla button. Client only.
 */
final class FlatButton extends AbstractButton {
    static final int HEIGHT = 18;
    private static final int PAD = 8;

    private final Runnable action;
    private final boolean primary;

    FlatButton(int x, int y, int width, Component label, boolean primary, Runnable action) {
        super(x, y, width, HEIGHT, label);
        this.action = action;
        this.primary = primary;
    }

    /** Width that fits {@code label} with padding. */
    static int widthFor(Font font, Component label) {
        return font.width(label) + PAD * 2;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean hot = active && isHoveredOrFocused();
        int fill;
        int edge;
        int text;
        if (!active) {
            fill = 0xFF1C1C1C;
            edge = 0xFF2A2A2A;
            text = MycelTheme.OFF;
        } else if (primary) {
            fill = hot ? MycelTheme.ACCENT : MycelTheme.ACCENT_DARK;
            edge = hot ? 0xFFFF8A80 : MycelTheme.ACCENT;
            text = MycelTheme.HEADING;
        } else {
            fill = hot ? MycelTheme.BORDER : MycelTheme.SURFACE;
            edge = hot ? 0xFF5A5A5A : MycelTheme.BORDER;
            text = hot ? MycelTheme.HEADING : MycelTheme.TEXT;
        }
        gfx.fill(x0, y0, x1, y1, edge);
        gfx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        Font font = Minecraft.getInstance().font;
        String label = MycelTheme.fit(font, getMessage().getString(), getWidth() - 6);
        gfx.centeredText(font, label, x0 + getWidth() / 2, y0 + (getHeight() - 8) / 2, text);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
