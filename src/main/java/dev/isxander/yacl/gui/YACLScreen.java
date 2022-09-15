package dev.isxander.yacl.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.OptionFlag;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.api.utils.Dimension;
import dev.isxander.yacl.api.utils.OptionUtils;
import dev.isxander.yacl.impl.YACLConstants;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class YACLScreen extends Screen {
    public final YetAnotherConfigLib config;
    public int currentCategoryIdx;

    private final Screen parent;

    public OptionListWidget optionList;
//    public final List<CategoryWidget> categoryButtons;
    public CategoryListWidget categoryList;
    public TooltipButtonWidget finishedSaveButton, cancelResetButton, undoButton;
    public SearchFieldWidget searchFieldWidget;

    public Text saveButtonMessage;
    public Text saveButtonTooltipMessage;
    private int saveButtonMessageTime;


    public YACLScreen(YetAnotherConfigLib config, Screen parent) {
        super(config.title());
        this.config = config;
        this.parent = parent;
//        this.categoryButtons = new ArrayList<>();
        this.currentCategoryIdx = 0;
    }

    @Override
    protected void init() {
//        categoryButtons.clear();
        int columnWidth = width / 3;
        int padding = columnWidth / 20;
        columnWidth = Math.min(columnWidth, 400);
        int paddedWidth = columnWidth - padding * 2;
//        Dimension<Integer> categoryDim = Dimension.ofInt(width / 3 / 2, padding, paddedWidth, 20);
//        int idx = 0;
//        for (ConfigCategory category : config.categories()) {
//            CategoryWidget categoryWidget = new CategoryWidget(
//                    this,
//                    category,
//                    idx,
//                    categoryDim.x() - categoryDim.width() / 2, categoryDim.y(),
//                    categoryDim.width(), categoryDim.height()
//            );
//
//            categoryButtons.add(categoryWidget);
//            addDrawableChild(categoryWidget);
//
//            idx++;
//            categoryDim.move(0, 21);
//        }

        Dimension<Integer> actionDim = Dimension.ofInt(width / 3 / 2, height - padding - 20, paddedWidth, 20);
        finishedSaveButton = new TooltipButtonWidget(this, actionDim.x() - actionDim.width() / 2, actionDim.y(), actionDim.width(), actionDim.height(), Text.empty(), Text.empty(), (btn) -> {
            saveButtonMessage = null;

            if (pendingChanges()) {
                Set<OptionFlag> flags = new HashSet<>();
                OptionUtils.forEachOptions(config, option -> {
                    if (option.applyValue()) {
                        flags.addAll(option.flags());
                    }
                });
                OptionUtils.forEachOptions(config, option -> {
                    if (option.changed()) {
                        YACLConstants.LOGGER.error("'{}' was saved as '{}' but the changes don't seem to have applied. (Maybe binding is immutable?)", option.name().getString(), option.pendingValue());
                        setSaveButtonMessage(Text.translatable("yacl.gui.fail_apply").formatted(Formatting.RED), Text.translatable("yacl.gui.fail_apply.tooltip"));
                    }
                });
                config.saveFunction().run();

                flags.forEach(flag -> flag.accept(client));
            } else close();
        });
        actionDim.expand(-actionDim.width() / 2 - 2, 0).move(-actionDim.width() / 2 - 2, -22);
        cancelResetButton = new TooltipButtonWidget(this, actionDim.x() - actionDim.width() / 2, actionDim.y(), actionDim.width(), actionDim.height(), Text.empty(), Text.empty(), (btn) -> {
            if (pendingChanges()) {
                OptionUtils.forEachOptions(config, Option::forgetPendingValue);
                close();
            } else {
                OptionUtils.forEachOptions(config, Option::requestSetDefault);
            }

        });
        actionDim.move(actionDim.width() + 4, 0);
        undoButton = new TooltipButtonWidget(this, actionDim.x() - actionDim.width() / 2, actionDim.y(), actionDim.width(), actionDim.height(), Text.translatable("yacl.gui.undo"), Text.translatable("yacl.gui.undo.tooltip"), (btn) -> {
            OptionUtils.forEachOptions(config, Option::forgetPendingValue);
        });

        searchFieldWidget = new SearchFieldWidget(this, textRenderer, width / 3 / 2 - paddedWidth / 2 + 1, undoButton.y - 22, paddedWidth - 2, 18, Text.translatable("yacl.gui.search"), Text.translatable("yacl.gui.search"));

        categoryList = new CategoryListWidget(client, this, width, height);
        addSelectableChild(categoryList);

        updateActionAvailability();
        addDrawableChild(searchFieldWidget);
        addDrawableChild(cancelResetButton);
        addDrawableChild(undoButton);
        addDrawableChild(finishedSaveButton);

        optionList = new OptionListWidget(this, client, width, height);
        addSelectableChild(optionList);

        config.initConsumer().accept(this);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        super.render(matrices, mouseX, mouseY, delta);
        categoryList.render(matrices, mouseX, mouseY, delta);
        searchFieldWidget.render(matrices, mouseX, mouseY, delta);
        optionList.render(matrices, mouseX, mouseY, delta);

        categoryList.postRender(matrices, mouseX, mouseY, delta);
        optionList.postRender(matrices, mouseX, mouseY, delta);

        for (Element child : children()) {
            if (child instanceof TooltipButtonWidget tooltipButtonWidget) {
                tooltipButtonWidget.renderHoveredTooltip(matrices, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (optionList.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (optionList.charTyped(chr, modifiers)) {
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    public void changeCategory(int idx) {
        currentCategoryIdx = idx;
        optionList.refreshOptions();
    }

    private void updateActionAvailability() {
        boolean pendingChanges = pendingChanges();

        undoButton.active = pendingChanges;
        finishedSaveButton.setMessage(pendingChanges ? Text.translatable("yacl.gui.save") : Text.translatable("gui.done"));
        finishedSaveButton.setTooltip(pendingChanges ? Text.translatable("yacl.gui.save.tooltip") : Text.translatable("yacl.gui.finished.tooltip"));
        cancelResetButton.setMessage(pendingChanges ? Text.translatable("gui.cancel") : Text.translatable("controls.reset"));
        cancelResetButton.setTooltip(pendingChanges ? Text.translatable("yacl.gui.cancel.tooltip") : Text.translatable("yacl.gui.reset.tooltip"));
    }

    @Override
    public void tick() {
        searchFieldWidget.tick();
        if (!searchFieldWidget.getText().isEmpty() && currentCategoryIdx != -1) {
            changeCategory(-1);
        }
        if (searchFieldWidget.getText().isEmpty() && currentCategoryIdx == -1) {
            changeCategory(0);
        }

        updateActionAvailability();

        if (saveButtonMessage != null) {
            if (saveButtonMessageTime > 140) {
                saveButtonMessage = null;
                saveButtonTooltipMessage = null;
                saveButtonMessageTime = 0;
            } else {
                saveButtonMessageTime++;
                finishedSaveButton.setMessage(saveButtonMessage);
                if (saveButtonTooltipMessage != null) {
                    finishedSaveButton.setTooltip(saveButtonTooltipMessage);
                }
            }
        }
    }

    private void setSaveButtonMessage(Text message, Text tooltip) {
        saveButtonMessage = message;
        saveButtonTooltipMessage = tooltip;
        saveButtonMessageTime = 0;
    }

    private boolean pendingChanges() {
        AtomicBoolean pendingChanges = new AtomicBoolean(false);
        OptionUtils.consumeOptions(config, (option) -> {
            if (option.changed()) {
                pendingChanges.set(true);
                return true;
            }
            return false;
        });

        return pendingChanges.get();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (pendingChanges()) {
            setSaveButtonMessage(Text.translatable("yacl.gui.save_before_exit").formatted(Formatting.RED), Text.translatable("yacl.gui.save_before_exit.tooltip"));
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    public static void renderMultilineTooltip(MatrixStack matrices, TextRenderer textRenderer, MultilineText text, int x, int y, int screenWidth, int screenHeight) {
        if (text.count() > 0) {
            int maxWidth = text.getMaxWidth();
            int lineHeight = textRenderer.fontHeight + 1;
            int height = text.count() * lineHeight;

            int drawX = x + 12;
            int drawY = y - 12;
            if (drawX + maxWidth > screenWidth) {
                drawX -= 28 + maxWidth;
            }

            if (drawY + height + 6 > screenHeight) {
                drawY = screenHeight - height - 6;
            }

            matrices.push();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuffer();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY - 4, drawX + maxWidth + 3, drawY - 3, 400, -267386864, -267386864);
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY + height + 3, drawX + maxWidth + 3, drawY + height + 4, 400, -267386864, -267386864);
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY - 3, drawX + maxWidth + 3, drawY + height + 3, 400, -267386864, -267386864);
            fillGradient(matrix4f, bufferBuilder, drawX - 4, drawY - 3, drawX - 3, drawY + height + 3, 400, -267386864, -267386864);
            fillGradient(matrix4f, bufferBuilder, drawX + maxWidth + 3, drawY - 3, drawX + maxWidth + 4, drawY + height + 3, 400, -267386864, -267386864);
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY - 3 + 1, drawX - 3 + 1, drawY + height + 3 - 1, 400, 1347420415, 1344798847);
            fillGradient(matrix4f, bufferBuilder, drawX + maxWidth + 2, drawY - 3 + 1, drawX + maxWidth + 3, drawY + height + 3 - 1, 400, 1347420415, 1344798847);
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY - 3, drawX + maxWidth + 3, drawY - 3 + 1, 400, 1347420415, 1347420415);
            fillGradient(matrix4f, bufferBuilder, drawX - 3, drawY + height + 2, drawX + maxWidth + 3, drawY + height + 3, 400, 1344798847, 1344798847);
            RenderSystem.enableDepthTest();
            RenderSystem.disableTexture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferRenderer.drawWithShader(bufferBuilder.end());
            RenderSystem.disableBlend();
            RenderSystem.enableTexture();
            matrices.translate(0.0, 0.0, 400.0);

            text.drawWithShadow(matrices, drawX, drawY, lineHeight, -1);

            matrices.pop();
        }
    }
}
