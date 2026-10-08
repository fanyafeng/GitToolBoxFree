package com.pom.gittoolbox.statusbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.pom.gittoolbox.settings.GitToolBoxSettings;
import com.pom.gittoolbox.util.I18nManager;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 极致优化的状态栏组件：
 * 1. 1.2s 防抖合流，消除 IDE 频繁触发 GIT_REPO_CHANGE 导致的进程轰炸
 * 2. 内存优先分支探测（无上游分支时 0 外部进程调用）
 * 3. 差分更新（内容未变化时不重复触发 status bar 全局 re-layout）
 * 4. 多模块/多仓库自适应：自动跟随当前激活的编辑文件切换对应仓库信息
 * 5. 线程安全并发防护
 */
public class GitStatusBarWidget implements CustomStatusBarWidget, GitRepositoryChangeListener, Disposable {

    public static final String ID = "com.pom.gittoolbox.statusbar.GitStatusBarWidget";

    private final Project project;
    private final JBLabel label = new JBLabel();
    private StatusBar statusBar;
    private final Alarm updateAlarm;
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private volatile String lastRenderedText = "";

    public GitStatusBarWidget(Project project) {
        this.project = project;
        this.updateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        this.label.setBorder(JBUI.Borders.empty(0, 6));

        this.label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    showActionPopup(e);
                }
            }
        });

        // 订阅 Git 仓库变化事件
        project.getMessageBus().connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, this);

        // 订阅活动编辑器切换事件（多模块/多仓库智能跟随）
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                scheduleUpdate(300);
            }
        });

        scheduleUpdate(0);
    }

    @NotNull
    @Override
    public String ID() {
        return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Nullable
    @Override
    public JComponent getComponent() {
        return label;
    }

    @Override
    public void repositoryChanged(@NotNull GitRepository repository) {
        // 仓库变动时防抖 1200ms，合并频繁的文件保存/索引刷新事件
        scheduleUpdate(1200);
    }

    public void updateStatus() {
        scheduleUpdate(0);
    }

    private void scheduleUpdate(int delayMs) {
        if (project.isDisposed() || updateAlarm.isDisposed()) {
            return;
        }

        updateAlarm.cancelAllRequests();
        updateAlarm.addRequest(() -> {
            if (project.isDisposed()) return;
            if (!isUpdating.compareAndSet(false, true)) {
                return;
            }
            try {
                doUpdateStatus();
            } finally {
                isUpdating.set(false);
            }
        }, delayMs);
    }

    private void doUpdateStatus() {
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state == null || !state.statusBarEnabled) {
            ApplicationManager.getApplication().invokeLater(() -> {
                label.setVisible(false);
                lastRenderedText = "";
            });
            return;
        }

        GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
        List<GitRepository> repos = manager.getRepositories();
        if (repos.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                label.setVisible(false);
                lastRenderedText = "";
            });
            return;
        }

        // 优先获取当前正在编辑的激活文件对应的 Git 仓库
        GitRepository repo = null;
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length > 0 && selectedFiles[0] != null) {
            repo = manager.getRepositoryForFileQuick(selectedFiles[0]);
        }
        if (repo == null) {
            repo = repos.get(0);
        }

        String branchName = repo.getCurrentBranchName();
        if (branchName == null || branchName.isEmpty()) {
            branchName = I18nManager.isEnglish() ? "detached HEAD" : "分离头指针(HEAD)";
        }

        int ahead = 0;
        int behind = 0;
        boolean hasUpstream = false;

        // 核心优化：先纯内存检查是否配置了远程追踪分支，若无上游，直接 0 开销返回，绝不执行外部进程
        GitBranchTrackInfo trackInfo = repo.getBranchTrackInfo(branchName);
        if (trackInfo != null) {
            VirtualFile root = repo.getRoot();
            GitLineHandler handler = new GitLineHandler(project, new File(root.getPath()), GitCommand.REV_LIST);
            handler.addParameters("--left-right", "--count", "HEAD...@{u}");
            handler.setSilent(true);

            GitCommandResult result = Git.getInstance().runCommand(handler);
            if (result.success() && !result.getOutput().isEmpty()) {
                String line = result.getOutput().get(0).trim();
                int spaceIndex = line.indexOf(' ');
                if (spaceIndex < 0) {
                    spaceIndex = line.indexOf('\t');
                }
                if (spaceIndex > 0) {
                    try {
                        ahead = Integer.parseInt(line.substring(0, spaceIndex).trim());
                        behind = Integer.parseInt(line.substring(spaceIndex + 1).trim());
                        hasUpstream = true;
                    } catch (Exception ignored) {}
                }
            }
        }

        final String finalBranch = branchName;
        final int finalAhead = ahead;
        final int finalBehind = behind;
        final boolean finalHasUpstream = hasUpstream;
        final String repoName = repo.getRoot().getName();
        final boolean en = I18nManager.isEnglish();

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            StringBuilder sb = new StringBuilder("🌿 " + finalBranch);
            if (finalHasUpstream) {
                if (finalAhead > 0 || finalBehind > 0) {
                    sb.append(" [");
                    if (finalAhead > 0) sb.append(I18nManager.getStatusAhead(finalAhead));
                    if (finalAhead > 0 && finalBehind > 0) sb.append(" | ");
                    if (finalBehind > 0) sb.append(I18nManager.getStatusBehind(finalBehind));
                    sb.append("]");
                } else {
                    sb.append(" ").append(I18nManager.getStatusUpToDate());
                }
            } else {
                sb.append(" ").append(I18nManager.getStatusNoUpstream());
            }

            String currentText = sb.toString();

            // 核心优化：如果文本未发生变化，坚决不触发 statusBar.updateWidget 全局重绘
            if (currentText.equals(lastRenderedText) && label.isVisible()) {
                return;
            }
            lastRenderedText = currentText;

            label.setText(currentText);
            label.setVisible(true);

            // 气泡 Tooltip
            String tooltip;
            if (en) {
                tooltip = "<html><b>GitToolBoxFree</b><br/>"
                        + "─────────────────────<br/>"
                        + "Repository: " + repoName + "<br/>"
                        + "Branch: " + finalBranch + "<br/>"
                        + "Ahead: " + (finalHasUpstream ? finalAhead : "no upstream") + "<br/>"
                        + "Behind: " + (finalHasUpstream ? finalBehind : "no upstream") + "<br/>"
                        + "<i>Click to show actions</i></html>";
            } else {
                tooltip = "<html><b>GitToolBoxFree</b><br/>"
                        + "─────────────────────<br/>"
                        + "项目仓库: " + repoName + "<br/>"
                        + "当前分支: " + finalBranch + "<br/>"
                        + "超前提交: " + (finalHasUpstream ? finalAhead + " 个" : "未追踪远程") + "<br/>"
                        + "落后提交: " + (finalHasUpstream ? finalBehind + " 个" : "未追踪远程") + "<br/>"
                        + "<i>点击展开快捷操作菜单</i></html>";
            }
            label.setToolTipText(tooltip);

            if (statusBar != null) {
                statusBar.updateWidget(ID);
            }
        });
    }

    private void showActionPopup(MouseEvent e) {
        JBPopupMenu menu = new JBPopupMenu();

        JMenuItem pullItem = new JMenuItem(I18nManager.getMenuPull());
        pullItem.addActionListener(ev -> executeGitAction(GitCommand.PULL));

        JMenuItem pushItem = new JMenuItem(I18nManager.getMenuPush());
        pushItem.addActionListener(ev -> executeGitAction(GitCommand.PUSH));

        JMenuItem refreshItem = new JMenuItem(I18nManager.getMenuRefresh());
        refreshItem.addActionListener(ev -> {
            executeGitAction(GitCommand.FETCH);
            scheduleUpdate(0);
        });

        menu.add(pullItem);
        menu.add(pushItem);
        menu.addSeparator();
        menu.add(refreshItem);

        menu.show(label, e.getX(), e.getY());
    }

    private void executeGitAction(GitCommand command) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
            List<GitRepository> repos = manager.getRepositories();
            if (repos.isEmpty()) return;

            // 优先针对当前激活的仓库执行
            GitRepository repo = null;
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
            if (selectedFiles.length > 0 && selectedFiles[0] != null) {
                repo = manager.getRepositoryForFileQuick(selectedFiles[0]);
            }
            if (repo == null) {
                repo = repos.get(0);
            }

            GitLineHandler handler = new GitLineHandler(project, new File(repo.getRoot().getPath()), command);
            Git.getInstance().runCommand(handler);
            scheduleUpdate(0);
        });
    }

    @Override
    public void dispose() {
        if (!updateAlarm.isDisposed()) {
            Disposer.dispose(updateAlarm);
        }
        label.setVisible(false);
    }
}
