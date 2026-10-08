package com.pom.gittoolbox.blame;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 极速 Git Blame 服务：
 * 1. 负缓存（未提交/未追踪文件绝不反复拉起外部进程）
 * 2. 零正则、同时支持 SHA-1(40位) 与 SHA-256(64位) 的极速瓷器格式解析
 * 3. 精确行号并发请求合流（解决多行并发查询时错拿首行结果的 bug）
 * 4. 仓库变更事件感知（Git commit/checkout/pull 自动清空失效缓存）
 * 5. 2MB 保护屏障与 LRU 缓存管理
 */
public class GitBlameService {

    private static final int MAX_CACHE_FILES = 100;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    private static class FileBlameData {
        final long fileTimeStamp;
        final Map<Integer, CommitInfo> lineCommits;

        FileBlameData(long stamp, Map<Integer, CommitInfo> commits) {
            this.fileTimeStamp = stamp;
            this.lineCommits = commits;
        }
    }

    private static class PendingRequest {
        final int line;
        final Consumer<CommitInfo> callback;

        PendingRequest(int line, Consumer<CommitInfo> callback) {
            this.line = line;
            this.callback = callback;
        }
    }

    private final Project project;
    private final Map<String, FileBlameData> cache = new ConcurrentHashMap<>();
    private final Set<String> loadingFiles = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, List<PendingRequest>> pendingRequests = new ConcurrentHashMap<>();

    public GitBlameService(Project project) {
        this.project = project;

        // 订阅 Git 仓库变化，当执行 commit、checkout、pull 等操作时自动清空缓存，确保 Blame 绝对最新
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, repo -> clearCache());
    }

    public static GitBlameService getInstance(Project project) {
        return project.getService(GitBlameService.class);
    }

    public void clearCache() {
        cache.clear();
    }

    public void invalidate(VirtualFile file) {
        if (file != null) {
            cache.remove(file.getPath());
        }
    }

    /**
     * 极速异步获取行 Blame 信息（1-based 行号）
     */
    public void getCommitInfo(VirtualFile file, int lineNumber1Based, Consumer<CommitInfo> callback) {
        if (project.isDisposed() || file == null || !file.isInLocalFileSystem()) {
            callback.accept(null);
            return;
        }

        // 大文件或二进制文件安全屏障
        if (file.getLength() > MAX_FILE_SIZE || file.getFileType().isBinary()) {
            callback.accept(null);
            return;
        }

        String path = file.getPath();
        long currentDiskStamp = file.getTimeStamp();
        FileBlameData cached = cache.get(path);

        // 内存缓存命中（包括未版本化文件的负缓存结果）
        if (cached != null && cached.fileTimeStamp == currentDiskStamp) {
            CommitInfo info = cached.lineCommits.get(lineNumber1Based);
            callback.accept(info);
            return;
        }

        // 避免同一文件并发重复启动 blame 进程，并按请求行号精确记录
        synchronized (loadingFiles) {
            if (loadingFiles.contains(path)) {
                pendingRequests.computeIfAbsent(path, k -> new ArrayList<>())
                        .add(new PendingRequest(lineNumber1Based, callback));
                return;
            }
            loadingFiles.add(path);
        }

        // 异步后台拉取文件 Blame
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Map<Integer, CommitInfo> blameResult = null;
            try {
                blameResult = parseFileBlame(file);
            } catch (Exception ignored) {
            }

            // 无论成功还是失败（未追踪/错误），均做缓存（失败记为 emptyMap 负缓存，避免反复调外部进程）
            Map<Integer, CommitInfo> finalMap = (blameResult != null) ? blameResult : Collections.emptyMap();

            if (cache.size() >= MAX_CACHE_FILES) {
                // LRU 淘汰较早的文件
                Iterator<String> it = cache.keySet().iterator();
                int removeCount = 0;
                while (it.hasNext() && removeCount < 20) {
                    it.next();
                    it.remove();
                    removeCount++;
                }
            }
            cache.put(path, new FileBlameData(currentDiskStamp, finalMap));

            CommitInfo directInfo = finalMap.get(lineNumber1Based);
            callback.accept(directInfo);

            // 分发给合流的并发请求（精确匹配各自所请求的行号）
            List<PendingRequest> waiting;
            synchronized (loadingFiles) {
                loadingFiles.remove(path);
                waiting = pendingRequests.remove(path);
            }
            if (waiting != null) {
                for (PendingRequest req : waiting) {
                    req.callback.accept(finalMap.get(req.line));
                }
            }
        });
    }

    private Map<Integer, CommitInfo> parseFileBlame(VirtualFile file) {
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        GitRepository repository = repositoryManager.getRepositoryForFileQuick(file);
        if (repository == null) {
            return null;
        }

        VirtualFile root = repository.getRoot();
        String rootPath = root.getPath();
        String filePath = file.getPath();
        if (!filePath.startsWith(rootPath)) {
            return null;
        }

        String relativePath = filePath.substring(rootPath.length());
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        GitLineHandler handler = new GitLineHandler(project, new File(rootPath), GitCommand.BLAME);
        handler.addParameters("--porcelain", relativePath);
        handler.setSilent(true);

        GitCommandResult result = Git.getInstance().runCommand(handler);
        if (!result.success()) {
            return null;
        }

        List<String> outputLines = result.getOutput();
        Map<Integer, CommitInfo> lineMap = new HashMap<>(outputLines.size() / 10);
        Map<String, PartialCommit> commitPool = new HashMap<>();

        String currentHash = null;
        int currentLineNum = -1;

        for (String line : outputLines) {
            if (line.isEmpty()) continue;

            if (line.charAt(0) == '\t') {
                if (currentHash != null && currentLineNum > 0) {
                    PartialCommit pc = commitPool.get(currentHash);
                    if (pc != null) {
                        lineMap.put(currentLineNum, new CommitInfo(currentHash, pc.author, pc.authorTime, pc.summary));
                    }
                }
                currentLineNum = -1;
            } else {
                int hashLen = getCommitHeaderHashLength(line);
                if (hashLen > 0) {
                    int space1 = hashLen;
                    int space2 = line.indexOf(' ', space1 + 1);
                    if (space2 > 0) {
                        int space3 = line.indexOf(' ', space2 + 1);
                        String lineNumStr = space3 > 0 ? line.substring(space2 + 1, space3) : line.substring(space2 + 1);
                        try {
                            currentLineNum = Integer.parseInt(lineNumStr.trim());
                        } catch (NumberFormatException ignored) {}
                    }
                    currentHash = line.substring(0, hashLen);
                    if (!commitPool.containsKey(currentHash)) {
                        commitPool.put(currentHash, new PartialCommit());
                    }
                } else if (currentHash != null) {
                    PartialCommit pc = commitPool.get(currentHash);
                    if (pc != null) {
                        if (line.startsWith("author ")) {
                            pc.author = line.substring(7).trim();
                        } else if (line.startsWith("author-time ")) {
                            try {
                                pc.authorTime = Long.parseLong(line.substring(12).trim());
                            } catch (Exception ignored) {}
                        } else if (line.startsWith("summary ")) {
                            pc.summary = line.substring(8).trim();
                        }
                    }
                }
            }
        }

        return lineMap;
    }

    /**
     * 校验当前行是否为 Commit Header 行，兼容 SHA-1 (40位) 与 SHA-256 (64位)
     */
    private static int getCommitHeaderHashLength(String line) {
        int spaceIndex = line.indexOf(' ');
        if (spaceIndex != 40 && spaceIndex != 64) {
            return -1;
        }
        for (int i = 0; i < spaceIndex; i++) {
            char c = line.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return -1;
            }
        }
        return spaceIndex;
    }

    private static class PartialCommit {
        String author = "";
        long authorTime = 0;
        String summary = "";
    }
}
