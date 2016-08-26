package ru.linachan.ctf.shell;

import ru.linachan.ctf.CTFPlugin;
import ru.linachan.ctf.common.ExploitExecutor;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.console.tables.Table;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.scheduler.YggdrasilTask;
import ru.linachan.yggdrasil.shell.YggdrasilShellCommand;
import ru.linachan.yggdrasil.shell.helpers.CommandAction;
import ru.linachan.yggdrasil.shell.helpers.ShellCommand;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ShellCommand(command = "executor", description = "Schedule exploit execution")
public class ExecutorCommand extends YggdrasilShellCommand {

    private CTFPlugin ctfPlugin;
    private static final Pattern TASKNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_\\-.]+$"
    );

    @Override
    @SuppressWarnings("unchecked")
    protected void init() throws IOException {
        ctfPlugin = YggdrasilCore.INSTANCE
            .getManager(YggdrasilPluginManager.class)
            .get(CTFPlugin.class);
    }

    @CommandAction("List scheduled executors")
    public void list() throws IOException {
        Table executors = new Table("Exploit Name", "Shell Command");
        for (YggdrasilTask executionTask: ctfPlugin.listExecutions()) {
            executors.addRow(
                executionTask.getTaskName(),
                ((ExploitExecutor) executionTask.getRunnableTask()).getCommand()
            );
        }
        console.writeTable(executors);
    }

    @CommandAction("Schedule exploit execution")
    public void schedule() throws IOException {
        if (kwargs.containsKey("name")&&kwargs.containsKey("cmd")) {
            int initDelay = Integer.parseInt(kwargs.getOrDefault("init-delay", "0"));
            int execPeriod = Integer.parseInt(kwargs.getOrDefault("exec-period", "120"));

            String exploitName = kwargs.get("name");
            Matcher exploitNameMatcher = TASKNAME_PATTERN.matcher(exploitName);

            if (exploitNameMatcher.matches()) {
                String[] exploitCommand = kwargs.get("cmd").split(" ");

                try {
                    ctfPlugin.scheduleExecution(exploitName, new ExploitExecutor(exploitCommand), initDelay, execPeriod);
                } catch (IllegalStateException e) {
                    console.writeLine("Execution with name {} already exists!", exploitName);
                    exit(1);
                }
            } else {
                console.writeLine("Execution name should match '^[a-zA-Z0-9_-.]+$' regex.");
                exit(1);
            }
        } else {
            console.writeLine("Either name or cmd is not provided.");
            exit(1);
        }
    }

    @CommandAction("Cancel exploit execution")
    public void cancel() throws IOException {
        if (kwargs.containsKey("name")) {
            ctfPlugin.cancelExecution(kwargs.get("name"));
        } else {
            console.writeLine("Execution name is not provided.");
            exit(1);
        }
    }

    @Override
    protected void onInterrupt() {

    }
}
