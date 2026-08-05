package com.riprod.patchly.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.util.message.MessageFormat;
import com.riprod.patchly.PatchManager;
import com.riprod.patchly.core.compile.CompileResult;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

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

    private static void sendInfo(@Nonnull CommandContext context, @Nonnull PatchManager manager) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.translation(LANG + "info.owner").param("owner", manager.getOwnerId()));
        lines.add(Message.translation(LANG + "info.pack").param("pack", manager.getOverridePackName()));
        lines.add(Message.translation(LANG + (manager.isWatchEnabled() ? "info.watcherOn" : "info.watcherOff")));

        List<Message> problems = collectProblems(manager);
        if (problems.isEmpty()) {
            lines.add(Message.translation(LANG + "info.clean"));
        } else {
            lines.addAll(problems);
        }

        context.sendMessage(MessageFormat.list(
                Message.translation(LANG + "info.header").param("version", PatchManager.PATCHER_VERSION),
                lines));
    }

    @Nonnull
    private static List<Message> collectProblems(@Nonnull PatchManager manager) {
        List<Message> out = new ArrayList<>();
        CompileResult result = manager.getLastResult();
        if (result != null) {
            for (CompileResult.MissingBase mb : result.missingBases()) {
                out.add(Message.translation(LANG + "info.missingBase")
                        .param("source", mb.source().toString())
                        .param("target", mb.target()));
            }
            for (CompileResult.UnresolvedImport ui : result.unresolvedImports()) {
                out.add(Message.translation(LANG + "info.unresolvedImport")
                        .param("ref", ui.ref())
                        .param("target", ui.fromTarget()));
            }
            for (CompileResult.UnresolvedExpression ue : result.unresolvedExpressions()) {
                out.add(Message.translation(LANG + "info.unresolvedExpression")
                        .param("where", ue.where())
                        .param("expression", ue.expression())
                        .param("reason", ue.reason()));
            }
            for (CompileResult.GatedSource gs : result.gatedSources()) {
                out.add(Message.translation(LANG + "info.gated")
                        .param("source", gs.source().toString())
                        .param("directive", gs.directive())
                        .param("condition", gs.condition()));
            }
        }
        for (String target : manager.getTrippedTargets()) {
            out.add(Message.translation(LANG + "info.tripped").param("target", target));
        }
        return out;
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
            int count = manager.forceReapply("command:reload").size();
            context.sendMessage(Message.translation(LANG + "reload.done").param("count", count));
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
            boolean stop = stopFlag.get(context);
            if (start == stop) {
                context.sendMessage(Message.translation(LANG + "watcher.needsFlag"));
                return;
            }

            if (!manager.setWatchEnabled(start)) {
                context.sendMessage(Message.translation(
                        LANG + (start ? "watcher.alreadyStarted" : "watcher.alreadyStopped")));
                return;
            }

            if (start) {
                context.sendMessage(Message.translation(LANG + "watcher.started"));
                // the monitor is edge-triggered, so nothing that changed while stopped is replayed
                context.sendMessage(Message.translation(LANG + "watcher.staleWarning"));
            } else {
                context.sendMessage(Message.translation(LANG + "watcher.stopped"));
            }
        }
    }
}
