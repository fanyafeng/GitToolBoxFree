package com.pom.gittoolbox.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 插件配置持久化组件
 */
@State(
    name = "com.pom.gittoolbox.settings.GitToolBoxSettings",
    storages = @Storage("GitToolBoxFree.xml")
)
public class GitToolBoxSettings implements PersistentStateComponent<GitToolBoxSettings.State> {

    public static class State {
        // 语言设置: "zh" (简体中文) 或 "en" (English)
        public String language = "zh";

        // 行内 Blame 开关与配置
        public boolean inlineBlameEnabled = true;
        public String blameTemplate = "{author} ({time_ago}) • {message}";
        public int blameDelayMs = 200; // 调优为更轻快的 200ms
        public boolean showIcon = true;

        // 状态栏组件开关
        public boolean statusBarEnabled = true;

        // 自动拉取开关
        public boolean autoFetchEnabled = true;
        public int autoFetchIntervalMinutes = 15;
    }

    private State myState = new State();

    public static GitToolBoxSettings getInstance() {
        return ApplicationManager.getApplication().getService(GitToolBoxSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
    }
}
