package com.interactivedisplay.core.window;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.internal.api.PublicEventDispatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

final class TextInputController {
    static final ResourceLocation SUBMIT_ACTION_ID = ResourceLocation.fromNamespaceAndPath(
            InteractiveDisplay.MOD_ID,
            "text_input_submit"
    );
    private static final String INPUT_KEY = "value";
    private static final int INPUT_WIDTH = 240;
    private static final int BUTTON_WIDTH = 100;

    private final MinecraftServer server;
    private final WindowStateStore stateStore;
    private final DisplayEntityFactory entityFactory;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    TextInputController(MinecraftServer server, WindowStateStore stateStore, DisplayEntityFactory entityFactory) {
        this.server = server;
        this.stateStore = stateStore;
        this.entityFactory = entityFactory;
    }

    ActionExecutionResult open(UUID owner, UiHitResult hitResult) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "text input 대상 플레이어를 찾을 수 없음");
        }
        if (!(hitResult.runtime().definition() instanceof TextInputComponentDefinition input)) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "text input component가 아님");
        }

        this.sessions.put(owner, new Session(hitResult.windowId(), hitResult.componentId()));

        Input textInput = new Input(
                INPUT_KEY,
                new TextInput(
                        INPUT_WIDTH,
                        Component.literal(input.dialogLabel()),
                        true,
                        hitResult.runtime().inputValue(),
                        input.maxLength(),
                        Optional.empty()
                )
        );
        CommonDialogData common = new CommonDialogData(
                Component.literal(input.dialogTitle()),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                List.of(textInput)
        );
        ActionButton confirm = new ActionButton(
                new CommonButtonData(Component.literal(input.confirmLabel()), BUTTON_WIDTH),
                Optional.of(new CustomAll(SUBMIT_ACTION_ID, Optional.empty()))
        );
        ActionButton cancel = new ActionButton(
                new CommonButtonData(Component.literal(input.cancelLabel()), BUTTON_WIDTH),
                Optional.empty()
        );
        Dialog dialog = new MultiActionDialog(common, List.of(confirm), Optional.of(cancel), 1);
        player.openDialog(Holder.direct(dialog));
        return ActionExecutionResult.success("text input dialog 열기 완료");
    }

    boolean handleCustomClickAction(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!SUBMIT_ACTION_ID.equals(packet.id())) {
            return false;
        }

        Session session = this.sessions.remove(player.getUUID());
        if (session == null) {
            return true;
        }

        String submitted = payloadValue(packet.payload()).orElse("");
        WindowInstance instance = this.stateStore.findWindow(player.getUUID(), session.windowId());
        if (instance == null) {
            return true;
        }

        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (!session.componentId().equals(runtime.definition().id())) {
                continue;
            }
            if (!(runtime.definition() instanceof TextInputComponentDefinition input)) {
                return true;
            }

            String value = submitted;
            if (value.length() > input.maxLength()) {
                value = value.substring(0, input.maxLength());
            }
            this.entityFactory.updateTextInputValue(this.server, player.getUUID(), runtime, input, value);
            PublicEventDispatcher.fireTextInputSubmitted(player.getUUID(), session.windowId(), session.componentId(), runtime.inputValue());
            return true;
        }
        return true;
    }

    void clear(UUID owner) {
        this.sessions.remove(owner);
    }

    private static Optional<String> payloadValue(Optional<Tag> payload) {
        if (payload.isEmpty() || !(payload.get() instanceof CompoundTag compound)) {
            return Optional.empty();
        }
        return compound.getString(INPUT_KEY);
    }

    private record Session(String windowId, String componentId) {
    }
}
