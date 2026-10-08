package com.pom.gittoolbox.blame;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 极致优化的行内代码责任人绘制器：
 * 预计算宽度、预解析字体与颜色，paint 期间 0 对象创建，0 GC 开销，保证高刷 120fps
 */
public class InlineBlameRenderer implements EditorCustomElementRenderer {

    private final Editor editor;
    private final String text;
    private final int cachedWidth;
    private final Font cachedFont;
    private final Color cachedColor;

    public InlineBlameRenderer(@NotNull Editor editor, @NotNull String text) {
        this.editor = editor;
        this.text = text;

        // 构造时预先解析字体与颜色，后续重绘期间不再产生任何堆内存分配
        this.cachedFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
        FontMetrics fm = editor.getContentComponent().getFontMetrics(cachedFont);
        this.cachedWidth = fm.stringWidth(text) + 24;

        Color themeColor = editor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
        if (themeColor == null) {
            themeColor = editor.getColorsScheme().getDefaultForeground();
        }
        if (themeColor == null) {
            themeColor = Color.GRAY;
        }
        this.cachedColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 135);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        return cachedWidth;
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
        Color oldColor = g.getColor();
        Font oldFont = g.getFont();

        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        g.setFont(cachedFont);
        g.setColor(cachedColor);

        int baseline = targetRegion.y + editor.getAscent();
        g.drawString(text, targetRegion.x + 16, baseline);

        g.setColor(oldColor);
        g.setFont(oldFont);
    }
}
