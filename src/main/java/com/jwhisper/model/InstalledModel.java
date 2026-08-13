package com.jwhisper.model;

public final class InstalledModel {
    public String id;
    public String installedAt;
    public String repository;
    public String revision;

    public InstalledModel() {
    }

    public InstalledModel(String id, String installedAt, String repository, String revision) {
        this.id = id;
        this.installedAt = installedAt;
        this.repository = repository;
        this.revision = revision;
    }
}
