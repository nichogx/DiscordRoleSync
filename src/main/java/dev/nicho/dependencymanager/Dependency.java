package dev.nicho.dependencymanager;

import java.net.URL;

class Dependency {

    private final URL url;
    private final String fileName;

    Dependency(URL url) {
        this.url = url;

        String[] segments = url.getPath().split("/");
        this.fileName = segments[segments.length - 1];
    }

    Dependency(URL url, String fileName) {
        this.url = url;
        this.fileName = fileName;
    }

    public URL getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }
}
