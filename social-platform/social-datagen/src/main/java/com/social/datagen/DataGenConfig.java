package com.social.datagen;

/**
 * Holds generation parameters for different data volume modes.
 */
public class DataGenConfig {

    public enum Mode {
        HUNDREDS, THOUSANDS
    }

    public final int users;
    public final int teams;
    public final int groups;
    public final int pages;
    public final int projects;
    public final int posts;
    public final int comments;
    public final int reactions;
    public final int attachments;

    private DataGenConfig(int users, int teams, int groups, int pages, int projects,
                          int posts, int comments, int reactions, int attachments) {
        this.users = users;
        this.teams = teams;
        this.groups = groups;
        this.pages = pages;
        this.projects = projects;
        this.posts = posts;
        this.comments = comments;
        this.reactions = reactions;
        this.attachments = attachments;
    }

    public static DataGenConfig forMode(Mode mode) {
        return switch (mode) {
            case HUNDREDS -> new DataGenConfig(
                    200,    // users
                    15,     // teams
                    20,     // groups
                    25,     // pages
                    10,     // projects
                    2000,   // posts
                    5000,   // comments
                    10000,  // reactions
                    500     // attachments
            );
            case THOUSANDS -> new DataGenConfig(
                    2000,   // users
                    100,    // teams
                    150,    // groups
                    200,    // pages
                    80,     // projects
                    20000,  // posts
                    50000,  // comments
                    100000, // reactions
                    5000    // attachments
            );
        };
    }

    public int totalEntities() {
        return users + teams + groups + pages + projects + posts + comments + reactions + attachments;
    }
}
