package com.jwhisper.model;

public final class InstalledModel {
    public String id;
    public String installedAt;
    public String repository;

    public InstalledModel() {
    }

    public InstalledModel(String id, String installedAt, String repository) {
        this.id = id;
        this.installedAt = installedAt;
        this.repository = repository;
    }
}
