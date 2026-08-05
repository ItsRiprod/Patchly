package com.riprod.patchly.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.riprod.patchly.PatchManager;
import com.riprod.patchly.core.compile.CompileResult;

import javax.annotation.Nonnull;

public final class PatchlyCommand extends CommandBase {
    private static final String LANG = "server.patchly.v1.";

    private final PatchManager manager;

    public PatchlyCommand(@Nonnull PatchManager manager) {
        super("patchly", LANG + "command.desc");
        this.manager = manager;
        requirePermission("patchly.command");
        addSubCommand(new InfoCommand(manager));
        addSubCommand(new ReloadCommand(manager));
        addSubCommand(new WatcherCommand(manager));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        sendInfo(context, manager);
    }

    // markupEnabled defaults to false and Message exposes no builder setter for it, so inline
    // lang tags only render once the flag is set on the node being sent
    @Nonnull
    private static Message markup(@Nonnull Message message) {
        message.getFormattedMessage().markupEnabled = true;
        return message;
    }

    private static void sendInfo(@Nonnull CommandContext context, @Nonnull PatchManager manager) {
        CompileResult result = manager.getLastResult();
        int applied = result == null ? 0 : result.outputs().size();
        int skipped = result == null ? 0 : result.gatedSources().size();
        int errors = manager.getTrippedTargets().size();
        if (result != null) {
            errors += result.missingBases().size()
                    + result.unresolvedImports().size()
                    + result.unresolvedExpressions().size();
        }

        context.sendMessage(markup(Message.translation(LANG + "info.body")
                .param("version", PatchManager.PATCHER_VERSION)
                .param("owner", manager.getOwnerId())
                .param("pack", manager.getOverridePackName())
                .param("state", markup(Message.translation(
                        LANG + (manager.isWatchEnabled() ? "enabled" : "disabled"))))
                .param("applied", applied)
                .param("skipped", skipped)
                .param("errors", errors)));
    }

    private static final class InfoCommand extends CommandBase {
        private final PatchManager manager;

        private InfoCommand(@Nonnull PatchManager manager) {
            super("info", LANG + "command.info.desc");
            this.manager = manager;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            sendInfo(context, manager);
        }
    }

    private static final class ReloadCommand extends CommandBase {
        private final PatchManager manager;

        private ReloadCommand(@Nonnull PatchManager manager) {
            super("reload", LANG + "command.reload.desc");
            this.manager = manager;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            context.sendMessage(markup(Message.translation(LANG + "reload.done")
                    .param("count", manager.forceReapply("command:reload").size())));
        }
    }

    private static final class WatcherCommand extends CommandBase {
        private final PatchManager manager;
        private final FlagArg startFlag;
        private final FlagArg stopFlag;

        private WatcherCommand(@Nonnull PatchManager manager) {
            super("watcher", LANG + "command.watcher.desc");
            this.manager = manager;
            this.startFlag = withFlagArg("start", LANG + "command.watcher.start.desc");
            this.stopFlag = withFlagArg("stop", LANG + "command.watcher.stop.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            boolean start = startFlag.get(context);
            if (start == stopFlag.get(context)) {
                context.sendMessage(markup(Message.translation(LANG + "watcher.needsFlag")));
                return;
            }
            if (!manager.setWatchEnabled(start)) {
                context.sendMessage(markup(Message.translation(LANG + "watcher.already")));
                return;
            }
            context.sendMessage(markup(Message.translation(
                    LANG + (start ? "watcher.started" : "watcher.stopped"))));
        }
    }
}
