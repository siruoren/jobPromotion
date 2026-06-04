package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;

public class RemoteJobInfo {

    private final String name;
    private final String fullDisplayName;
    private final boolean folder;
    private final String color;

    public RemoteJobInfo(@NonNull String name, @NonNull String fullDisplayName, boolean folder, String color) {
        this.name = name;
        this.fullDisplayName = fullDisplayName;
        this.folder = folder;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getFullDisplayName() {
        return fullDisplayName;
    }

    public boolean isFolder() {
        return folder;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "RemoteJobInfo{name='" + name + "', fullDisplayName='" + fullDisplayName + "', folder=" + folder + "}";
    }
}
