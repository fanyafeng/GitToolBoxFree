package com.pom.gittoolbox.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import com.pom.gittoolbox.blame.InlineBlameListener;

/**
 * 插件设置面板（支持用户自由选择语言：中文 / 英文）
 */
public class GitToolBoxConfigurable implements SearchableConfigurable {

    private JPanel mainPanel;
    private ComboBox<String> languageComboBox;
    private JBCheckBox inlineBlameCheckBox;
    private JBTextField templateField;
    private JSpinner delaySpinner;
    private JBCheckBox showIconCheckBox;
    private JBCheckBox statusBarCheckBox;
    private JBCheckBox autoFetchCheckBox;
    private JSpinner fetchIntervalSpinner;

    @NotNull
    @Override
    public String getId() {
        return "com.pom.gittoolbox.settings.GitToolBoxConfigurable";
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "GitToolBoxFree";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(JBUI.Borders.empty(12));

        // 0. 语言选择组 (Language Selection)
        JPanel langGroup = createSectionPanel("界面与显示语言 / Language");
        JPanel langRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        langRow.add(new JBLabel("选择显示语言 (Select Language)："));
        languageComboBox = new ComboBox<>(new String[]{"简体中文 (Chinese)", "English (英文)"});
        langRow.add(languageComboBox);
        langGroup.add(langRow);

        // 1. 行内 Blame 设置组
        JPanel blameGroup = createSectionPanel("行内代码责任人 / Inline Blame");
        inlineBlameCheckBox = new JBCheckBox("启用光标所在行末显示提交记录 (Enable Inline Blame)");
        showIconCheckBox = new JBCheckBox("在行内信息前显示头像图标 (Show Avatar Icon 👤)");

        JPanel templatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        templatePanel.add(new JBLabel("显示格式模版 (Display Template)："));
        templateField = new JBTextField(35);
        templatePanel.add(templateField);

        JBLabel templateHint = new JBLabel("变量/Variables: {author} 作者, {time_ago} 相对时间, {message} 提交信息, {hash} Commit哈希");
        templateHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);

        JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        delayPanel.add(new JBLabel("光标移动防抖延迟 (Debounce Delay)："));
        delaySpinner = new JSpinner(new SpinnerNumberModel(200, 50, 2000, 50));
        delayPanel.add(delaySpinner);
        delayPanel.add(new JBLabel(" 毫秒 / ms (建议 150~300ms)"));

        blameGroup.add(inlineBlameCheckBox);
        blameGroup.add(showIconCheckBox);
        blameGroup.add(templatePanel);
        blameGroup.add(templateHint);
        blameGroup.add(delayPanel);

        // 2. 状态栏设置组
        JPanel statusGroup = createSectionPanel("状态栏仓库指示器 / Status Bar");
        statusBarCheckBox = new JBCheckBox("在底部状态栏显示 Git 分支与超前/落后数 (Show Git Branch & Ahead/Behind)");
        statusGroup.add(statusBarCheckBox);

        // 3. 自动拉取设置组
        JPanel fetchGroup = createSectionPanel("后台自动拉取 / Auto Fetch");
        autoFetchCheckBox = new JBCheckBox("启用后台静默自动拉取 (Enable Background Silent git fetch)");
        JPanel intervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        intervalPanel.add(new JBLabel("自动拉取周期间隔 (Fetch Interval)："));
        fetchIntervalSpinner = new JSpinner(new SpinnerNumberModel(15, 1, 120, 5));
        intervalPanel.add(fetchIntervalSpinner);
        intervalPanel.add(new JBLabel(" 分钟 / minutes"));

        fetchGroup.add(autoFetchCheckBox);
        fetchGroup.add(intervalPanel);

        mainPanel.add(langGroup);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(blameGroup);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(statusGroup);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(fetchGroup);
        mainPanel.add(Box.createVerticalGlue());

        return mainPanel;
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    @Override
    public boolean isModified() {
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state == null) return false;

        String selectedLang = languageComboBox.getSelectedIndex() == 1 ? "en" : "zh";

        return !selectedLang.equalsIgnoreCase(state.language) ||
                inlineBlameCheckBox.isSelected() != state.inlineBlameEnabled ||
                showIconCheckBox.isSelected() != state.showIcon ||
                !templateField.getText().equals(state.blameTemplate) ||
                !delaySpinner.getValue().equals(state.blameDelayMs) ||
                statusBarCheckBox.isSelected() != state.statusBarEnabled ||
                autoFetchCheckBox.isSelected() != state.autoFetchEnabled ||
                !fetchIntervalSpinner.getValue().equals(state.autoFetchIntervalMinutes);
    }

    @Override
    public void apply() {
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state == null) return;

        state.language = languageComboBox.getSelectedIndex() == 1 ? "en" : "zh";
        state.inlineBlameEnabled = inlineBlameCheckBox.isSelected();
        state.showIcon = showIconCheckBox.isSelected();
        state.blameTemplate = templateField.getText().trim();
        state.blameDelayMs = (Integer) delaySpinner.getValue();
        state.statusBarEnabled = statusBarCheckBox.isSelected();
        state.autoFetchEnabled = autoFetchCheckBox.isSelected();
        state.autoFetchIntervalMinutes = (Integer) fetchIntervalSpinner.getValue();

        if (!state.inlineBlameEnabled) {
            InlineBlameListener.clearAllInlays();
        }
    }

    @Override
    public void reset() {
        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state == null) return;

        languageComboBox.setSelectedIndex("en".equalsIgnoreCase(state.language) ? 1 : 0);
        inlineBlameCheckBox.setSelected(state.inlineBlameEnabled);
        showIconCheckBox.setSelected(state.showIcon);
        templateField.setText(state.blameTemplate);
        delaySpinner.setValue(state.blameDelayMs);
        statusBarCheckBox.setSelected(state.statusBarEnabled);
        autoFetchCheckBox.setSelected(state.autoFetchEnabled);
        fetchIntervalSpinner.setValue(state.autoFetchIntervalMinutes);
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}
