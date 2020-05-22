package dev.nicho.dependencymanager;

import java.net.URL;

/**
 * Describes a dependency
 */
class Dependency {

    /**
     * url of the dependency
     */
    private final URL url;

    /**
     * name of the file
     */
    private final String fileName;

    /**
     * Creates a dependency. The filename will be the name of the file in the URL.
     *
     * @param url the url of the dependency
     */
    Dependency(URL url) {
        this.url = url;

        String[] segments = url.getPath().split("/");
        this.fileName = segments[segments.length - 1];
    }

    /**
     * Creates a dependency with a custom filename.
     *
     * @param url the url of the dependency
     * @param fileName the custom filename
     */
    Dependency(URL url, String fileName) {
        this.url = url;
        this.fileName = fileName;
    }

    /**
     * Gets the URL of the dependency
     *
     * @return the url
     */
    public URL getUrl() {
        return url;
    }

    /**
     * Gets the filename of the dependency
     *
     * @return the filename
     */
    public String getFileName() {
        return fileName;
    }
}
