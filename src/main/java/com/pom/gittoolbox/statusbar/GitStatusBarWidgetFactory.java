package com.pom.gittoolbox.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * 状态栏组件工厂
 */
public class GitStatusBarWidgetFactory implements StatusBarWidgetFactory {

    @NotNull
    @Override
    public String getId() {
        return GitStatusBarWidget.ID;
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "GitToolBoxFree 状态栏指示器";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return !project.isDisposed();
    }

    @NotNull
    @Override
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new GitStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        widget.dispose();
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
