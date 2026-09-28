package dev.xyat.contentstudio.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;

import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * 为自绘的小数偏移滚动区域（KineticScrollAnimator + 自绘滚动条）提供与 KineticScrollController 相同的中键跳转、
 * 0.5 秒悬停提示与选中闪烁。内部控制器只负责滑块悬停检测、跳转与闪烁；滚动状态、拖动与滚动条外观仍由页面自己负责，
 * 页面在控制器之后绘制自己的滚动条，完全覆盖控制器画出的滚动条，因此外观不变。
 * Gives a hand-drawn fractional-offset scroll area (KineticScrollAnimator plus a custom scrollbar) the same
 * middle-click jump, 0.5 s hover hint and selection flash as a KineticScrollController. The inner controller only
 * handles thumb hover detection, the jump and the flash; the page keeps its own scroll state, dragging and scrollbar
 * look, and draws its scrollbar after the controller's so it fully covers it and the visuals stay unchanged.
 */
public final class FractionalScrollJump {
    /** 逻辑偏移放大倍数，让整数控制器表达小数偏移 / Offset scale so the integer controller can express fractional offsets. */
    private static final int SCALE = 1000;

    private final KineticScrollController controller = new KineticScrollController();
    private boolean jumped;

    /**
     * @param target        跳转目标下标，-1 表示无（此时不提示也不跳转）/ Jump target index; -1 for none (no hint, no jump).
     * @param targetOffset  下标 → 逻辑偏移（与页面滚动状态同单位，未夹紧）/ Index to logical offset (page scroll units, unclamped).
     */
    public FractionalScrollJump(IntSupplier target, IntUnaryOperator targetOffset) {
        controller.bindSelection(target, index -> {
            jumped = true;
            return targetOffset.applyAsInt(index) * SCALE;
        });
    }

    /**
     * 取出自上次调用以来的中键跳转结果（已按范围夹紧的逻辑偏移），没有跳转时返回 NaN。须在本帧 {@link #track} 之前调用。
     * Takes the middle-click jump since the last call (a clamped logical offset), or NaN when there was none. Call it
     * before this frame's {@link #track}.
     */
    public double takeJump() {
        if (!jumped) return Double.NaN;
        jumped = false;
        return controller.offset() / (double) SCALE;
    }

    /**
     * 每帧在绘制页面自己的滚动条之前调用：同步范围与偏移并登记滑块悬停（拖动中不累计悬停，与标准控制器一致）。
     * Call every frame right before drawing the page's own scrollbar: syncs range and offset and registers thumb hover
     * (no hover accumulates while dragging, matching the standard controller).
     */
    public void track(KineticGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height,
                      int minThumbHeight, double offset, int maxOffset, int totalUnits, int visibleUnits,
                      boolean dragging) {
        controller.updateRange(Math.max(0, maxOffset) * SCALE, Math.max(0, totalUnits) * SCALE,
                Math.max(1, visibleUnits) * SCALE);
        controller.setOffset((int) Math.round(offset * SCALE));
        int pointerX = dragging ? Integer.MIN_VALUE : mouseX;
        int pointerY = dragging ? Integer.MIN_VALUE : mouseY;
        controller.render(graphics, pointerX, pointerY, x, y, width, height, minThumbHeight);
    }

    /** 在条目绘制之后调用：仅对正在闪烁的目标下标绘制闪烁 / Call after drawing an item: draws the flash only for the flashing target. */
    public void flash(KineticGraphics graphics, int index, int x, int y, int width, int height) {
        controller.renderSelectionFlash(graphics, index, x, y, width, height);
    }
}
