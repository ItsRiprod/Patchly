package com.riprod.patchly.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.riprod.patchly.PatchManager;
import com.riprod.patchly.core.compile.CompileResult;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        addSubCommand(new ExplainCommand(manager));
        addSubCommand(new VarsCommand(manager));
    }

    @Nonnull
    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 9007199254740992.0
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static final class VarsCommand extends CommandBase {
        private final PatchManager manager;

        private VarsCommand(@Nonnull PatchManager manager) {
            super("vars", LANG + "command.vars.desc");
            this.manager = manager;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Map<String, Map<String, Double>> vars = manager.getVars();
            boolean any = false;
            for (Map<String, Double> members : vars.values()) {
                if (!members.isEmpty()) any = true;
            }
            if (!any) {
                context.sendMessage(markup(Message.translation(LANG + "vars.empty")));
                return;
            }
            context.sendMessage(markup(Message.translation(LANG + "vars.header")));
            List<String> scopes = new ArrayList<>(vars.keySet());
            scopes.sort(null);
            for (String scope : scopes) {
                Map<String, Double> members = vars.get(scope);
                if (members.isEmpty()) continue;
                context.sendMessage(markup(Message.translation(LANG + "vars.scope").param("scope", scope)));
                List<String> names = new ArrayList<>(members.keySet());
                names.sort(null);
                for (String name : names) {
                    context.sendMessage(markup(Message.translation(LANG + "vars.line")
                            .param("name", name)
                            .param("value", number(members.get(name)))));
                }
            }
        }
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

    private static final class ExplainCommand extends CommandBase {
        private final PatchManager manager;
        private final RequiredArg<String> targetArg;

        private ExplainCommand(@Nonnull PatchManager manager) {
            super("explain", LANG + "command.explain.desc");
            this.manager = manager;
            this.targetArg = withRequiredArg("target", LANG + "command.explain.target.desc", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            CompileResult result = manager.getLastResult();
            if (result == null) {
                context.sendMessage(markup(Message.translation(LANG + "explain.noResult")));
                return;
            }

            String query = targetArg.get(context);
            List<String> matches = match(result, query);
            if (matches.isEmpty()) {
                context.sendMessage(markup(Message.translation(LANG + "explain.notFound")
                        .param("target", query)));
                return;
            }
            if (matches.size() > 1) {
                context.sendMessage(markup(Message.translation(LANG + "explain.ambiguous")
                        .param("target", query)
                        .param("matches", String.join(", ", matches))));
                return;
            }

            String target = matches.get(0);
            Path base = manager.getBasePath(target);
            context.sendMessage(markup(Message.translation(LANG + "explain.header")
                    .param("target", target)
                    .param("base", base == null ? "-" : base.toString())));

            List<CompileResult.Contribution> applied =
                    result.contributions().getOrDefault(target, List.of());
            for (CompileResult.Contribution c : applied) {
                context.sendMessage(markup(Message.translation(LANG + "explain.line")
                        .param("source", c.source().toString())
                        .param("kind", c.kind())
                        .param("priority", c.priority())));
            }

            for (CompileResult.GatedSource gs : result.gatedSources()) {
                if (!target.equals(gs.target())) continue;
                context.sendMessage(markup(Message.translation(LANG + "explain.gated")
                        .param("source", gs.source().toString())
                        .param("directive", gs.directive())
                        .param("condition", gs.condition())));
            }

            for (CompileResult.MissingBase mb : result.missingBases()) {
                if (target.equals(mb.target())) {
                    context.sendMessage(markup(Message.translation(LANG + "explain.missingBase")
                            .param("source", mb.source().toString())));
                }
            }
            for (CompileResult.UnresolvedImport ui : result.unresolvedImports()) {
                if (target.equals(ui.fromTarget())) {
                    context.sendMessage(markup(Message.translation(LANG + "explain.unresolvedImport")
                            .param("ref", ui.ref())));
                }
            }
            for (CompileResult.UnresolvedExpression ue : result.unresolvedExpressions()) {
                if (!target.equals(ue.target())) continue;
                context.sendMessage(markup(Message.translation(LANG + "explain.unresolvedExpression")
                        .param("where", ue.where())
                        .param("expression", ue.expression())
                        .param("reason", ue.reason())));
                CompileResult.GatedSource gatedScope = gatedVars(result, ue.missingScope());
                if (gatedScope != null) {
                    context.sendMessage(markup(Message.translation(LANG + "explain.gatedScope")
                            .param("scope", ue.missingScope())
                            .param("source", gatedScope.source().toString())
                            .param("directive", gatedScope.directive())
                            .param("condition", gatedScope.condition())));
                }
            }
            if (manager.getTrippedTargets().contains(target)) {
                context.sendMessage(markup(Message.translation(LANG + "explain.tripped")));
            }
        }

        private static CompileResult.GatedSource gatedVars(@Nonnull CompileResult result, String scope) {
            if (scope == null) return null;
            String file = scope + ".vars";
            for (CompileResult.GatedSource gs : result.gatedSources()) {
                Path name = gs.source().getFileName();
                if (name != null && name.toString().equals(file)) return gs;
            }
            return null;
        }

        @Nonnull
        private static List<String> match(@Nonnull CompileResult result, @Nonnull String query) {
            List<String> known = new ArrayList<>(result.outputs().keySet());
            for (CompileResult.GatedSource gs : result.gatedSources()) {
                if (!gs.target().isEmpty() && !known.contains(gs.target())) known.add(gs.target());
            }
            for (CompileResult.MissingBase mb : result.missingBases()) {
                if (!known.contains(mb.target())) known.add(mb.target());
            }

            List<String> exact = new ArrayList<>();
            List<String> loose = new ArrayList<>();
            for (String target : known) {
                if (target.equals(query)) {
                    exact.add(target);
                } else if (query.equals(stem(target)) || query.equals(fileName(target))) {
                    loose.add(target);
                }
            }
            return exact.isEmpty() ? loose : exact;
        }

        @Nonnull
        private static String fileName(@Nonnull String target) {
            int slash = target.lastIndexOf('/');
            return slash < 0 ? target : target.substring(slash + 1);
        }

        @Nonnull
        private static String stem(@Nonnull String target) {
            String name = fileName(target);
            int dot = name.lastIndexOf('.');
            return dot <= 0 ? name : name.substring(0, dot);
        }
    }
}
