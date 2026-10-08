package com.pom.gittoolbox.blame;

/**
 * 单行代码对应的提交元数据模型
 */
public class CommitInfo {
    private final String hash;
    private final String author;
    private final long authorTimeSeconds;
    private final String summary;

    public CommitInfo(String hash, String author, long authorTimeSeconds, String summary) {
        this.hash = hash != null ? hash : "";
        this.author = author != null ? author : "";
        this.authorTimeSeconds = authorTimeSeconds;
        this.summary = summary != null ? summary : "";
    }

    public String getHash() {
        return hash;
    }

    public String getShortHash() {
        return hash.length() > 7 ? hash.substring(0, 7) : hash;
    }

    public String getAuthor() {
        return author;
    }

    public long getAuthorTimeSeconds() {
        return authorTimeSeconds;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isUncommitted() {
        return hash.isEmpty() || hash.startsWith("00000000");
    }
}
