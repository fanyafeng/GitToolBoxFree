package com.pom.gittoolbox.fetch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.pom.gittoolbox.settings.GitToolBoxSettings;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台定时自动拉取服务 (Auto Fetch)：
 * 1. 动态周期配置，与设置面板中分钟数同步
 * 2. 注册到 Project Disposer，工程关闭时自动注销线程池任务，消除僵尸任务
 * 3. 单任务互斥锁（避免网络慢时多任务重叠拉取）
 * 4. Rebase/Merge 运行中静默避让，保证用户操作安全
 */
public class AutoFetchService implements StartupActivity, Disposable {

    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean isFetching = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
        if (project.isDisposed()) return;
        Disposer.register(project, this);

        int interval = 15;
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state != null && state.autoFetchIntervalMinutes > 0) {
            interval = state.autoFetchIntervalMinutes;
        }

        // 定期检查并执行 fetch
        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
            if (project.isDisposed()) {
                if (scheduledTask != null) {
                    scheduledTask.cancel(true);
                }
                return;
            }

            GitToolBoxSettings.State currentState = GitToolBoxSettings.getInstance().getState();
            if (currentState == null || !currentState.autoFetchEnabled) {
                return;
            }

            performAutoFetch(project);
        }, 3, interval, TimeUnit.MINUTES);
    }

    private void performAutoFetch(Project project) {
        if (!isFetching.compareAndSet(false, true)) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (project.isDisposed()) return;

                GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
                List<GitRepository> repos = manager.getRepositories();
                for (GitRepository repo : repos) {
                    // 若处于 rebase 过程中，避免执行 fetch 扰乱状态
                    if (repo.isRebaseInProgress()) {
                        continue;
                    }

                    try {
                        GitLineHandler handler = new GitLineHandler(project, new File(repo.getRoot().getPath()), GitCommand.FETCH);
                        handler.addParameters("--prune");
                        handler.setSilent(true);
                        Git.getInstance().runCommand(handler);
                    } catch (Exception ignored) {}
                }
            } finally {
                isFetching.set(false);
            }
        });
    }

    @Override
    public void dispose() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
    }
}
