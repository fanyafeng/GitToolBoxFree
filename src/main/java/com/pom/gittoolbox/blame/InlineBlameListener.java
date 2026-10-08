package com.pom.gittoolbox.blame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.pom.gittoolbox.settings.GitToolBoxSettings;
import com.pom.gittoolbox.util.ChineseTimeAgo;
import com.pom.gittoolbox.util.I18nManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极致优化的编辑器光标监听器：
 * 1. 同行移动 0 开销瞬时拦截（打字时零干扰）
 * 2. 文档修改监听：打字输入期间自动消除 Inlay，保持 IDE 原生输入响应
 * 3. 后台线程池防抖（POOLED_THREAD）：避免 UI 线程调度卡顿
 * 4. 连续按键/滚动期间不频繁重排 InlayModel，光标停顿后再批量更新
 * 5. 全生命周期 Disposer 树绑定，绝无内存泄露
 */
public class InlineBlameListener implements EditorFactoryListener {

    private static final Map<Editor, Inlay<?>> ACTIVE_INLAYS = new ConcurrentHashMap<>();
    private final Map<Editor, Alarm> alarms = new ConcurrentHashMap<>();
    private final Map<Editor, Disposable> disposables = new ConcurrentHashMap<>();
    private final Map<Editor, Integer> lastLineMap = new ConcurrentHashMap<>();

    public static void clearAllInlays() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Inlay<?> inlay : ACTIVE_INLAYS.values()) {
                if (inlay != null && inlay.isValid()) {
                    Disposer.dispose(inlay);
                }
            }
            ACTIVE_INLAYS.clear();
        });
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        // 过滤单行输入框、搜索框等非正常代码编辑器
        if (editor.isOneLineMode()) {
            return;
        }

        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        Disposable editorDisposable = Disposer.newDisposable("GitToolBoxInlineBlame:" + editor.hashCode());
        disposables.put(editor, editorDisposable);
        Disposer.register(project, editorDisposable);

        // 使用 POOLED_THREAD 后台线程池防抖，绝不占用 Swing 事件分发线程 (EDT)
        Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, editorDisposable);
        alarms.put(editor, alarm);

        // 光标监听器
        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent e) {
                onCaretMoved(editor, e.getCaret());
            }
        }, editorDisposable);

        // 文档修改监听：打字时立即清理行尾 Inlay，保证代码输入丝滑且不出现陈旧责任人信息
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                clearInlay(editor);
                Alarm a = alarms.get(editor);
                if (a != null && !a.isDisposed()) {
                    a.cancelAllRequests();
                }
            }
        }, editorDisposable);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        clearInlay(editor);
        alarms.remove(editor);
        lastLineMap.remove(editor);
        Disposable disposable = disposables.remove(editor);
        if (disposable != null) {
            Disposer.dispose(disposable);
        }
    }

    private void onCaretMoved(@NotNull Editor editor, Caret caret) {
        if (caret == null || caret != editor.getCaretModel().getPrimaryCaret()) {
            return;
        }

        int currentLine = caret.getLogicalPosition().line;
        Integer previousLine = lastLineMap.get(editor);

        // 核心优化：若光标仍停留在同一行（左右移动、单行打字移动），0 CPU 开销直接返回
        if (previousLine != null && previousLine == currentLine) {
            return;
        }

        lastLineMap.put(editor, currentLine);
        clearInlay(editor);

        Alarm alarm = alarms.get(editor);
        if (alarm == null || alarm.isDisposed()) {
            return;
        }

        // 取消上一行的待定请求
        alarm.cancelAllRequests();

        GitToolBoxSettings.State state = GitToolBoxSettings.getInstance().getState();
        if (state == null || !state.inlineBlameEnabled) {
            return;
        }

        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        Document document = editor.getDocument();
        if (currentLine < 0 || currentLine >= document.getLineCount()) {
            return;
        }

        int delay = Math.max(state.blameDelayMs, 80);

        // 在后台线程执行防抖等待与 Blame 解析，EDT 保持 100% 畅通
        alarm.addRequest(() -> {
            if (project.isDisposed() || editor.isDisposed()) return;

            VirtualFile file = editor.getVirtualFile();
            if (file == null) {
                file = FileDocumentManager.getInstance().getFile(document);
            }
            if (file == null || !file.isInLocalFileSystem()) {
                ApplicationManager.getApplication().invokeLater(() -> clearInlay(editor));
                return;
            }

            GitBlameService.getInstance(project).getCommitInfo(file, currentLine + 1, commitInfo -> {
                if (commitInfo == null || commitInfo.isUncommitted()) {
                    ApplicationManager.getApplication().invokeLater(() -> clearInlay(editor));
                    return;
                }

                String text = formatBlame(commitInfo, state);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (editor.isDisposed() || project.isDisposed()) return;
                    // 二次确认：光标是否仍在该行
                    int checkLine = editor.getCaretModel().getLogicalPosition().line;
                    if (checkLine != currentLine || currentLine >= document.getLineCount()) {
                        return;
                    }

                    clearInlay(editor);
                    try {
                        int lineEndOffset = document.getLineEndOffset(currentLine);
                        Inlay<?> inlay = editor.getInlayModel().addAfterLineEndElement(
                                lineEndOffset,
                                false,
                                new InlineBlameRenderer(editor, text)
                        );
                        if (inlay != null) {
                            ACTIVE_INLAYS.put(editor, inlay);
                        }
                    } catch (Exception ignored) {}
                });
            });
        }, delay);
    }

    private void clearInlay(Editor editor) {
        Inlay<?> oldInlay = ACTIVE_INLAYS.remove(editor);
        if (oldInlay != null && oldInlay.isValid()) {
            Disposer.dispose(oldInlay);
        }
    }

    private String formatBlame(CommitInfo info, GitToolBoxSettings.State state) {
        String template = state.blameTemplate;
        if (template == null || template.isEmpty()) {
            template = "{author} ({time_ago}) • {message}";
        }

        String timeAgo = I18nManager.formatTimeAgo(info.getAuthorTimeSeconds());
        String dateStr = ChineseTimeAgo.formatAbsoluteDate(info.getAuthorTimeSeconds());

        String result = template
                .replace("{author}", info.getAuthor())
                .replace("{time_ago}", timeAgo)
                .replace("{date}", dateStr)
                .replace("{message}", info.getSummary())
                .replace("{hash}", info.getShortHash());

        if (state.showIcon) {
            result = "👤 " + result;
        }
        return result;
    }
}
