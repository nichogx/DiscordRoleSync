package dev.nicho.dependencymanager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a list of dependency and downloads them to a folder
 */
public class DependencyManager {

    final int CONNECT_TIMEOUT = 4000;
    final int READ_TIMEOUT = 10000;

    private final List<Dependency> dependencies;
    private final File libFolder;

    /**
     * Constructor
     *
     * @param libFolder the folder where libraries should be downloaded to
     */
    public DependencyManager(File libFolder) {
        if (!libFolder.isDirectory()) {
            throw new IllegalArgumentException("libFolder must be a directory");
        }

        this.libFolder = libFolder;

        dependencies = new ArrayList<>();
    }

    /**
     * Adds a dependency with a default filename
     *
     * @param url the url of the dependency
     */
    public void addDependency(URL url) {
        dependencies.add(new Dependency(url));
    }

    /**
     * Adds a dependency with a custom filename
     *
     * @param url the url of the dependency
     * @param fileName the name of the file
     */
    public void addDependency(URL url, String fileName) {
        dependencies.add(new Dependency(url, fileName));
    }

    /**
     * Downloads all dependencies into the folder, if they are not present already
     *
     * @return the number of new dependencies downloaded
     * @throws IOException if an error occurs while downloading or saving the dependency
     */
    public int downloadAll() throws IOException {

        int newDownloaded = 0;
        for (final Dependency dep : dependencies) {

            final File dlFile = new File(libFolder, dep.getFileName());
            if (dlFile.exists()) continue;

            FileUtils.copyURLToFile(dep.getUrl(), dlFile, CONNECT_TIMEOUT, READ_TIMEOUT);
            newDownloaded++;

            if (!dlFile.exists()) throw new IOException("Dependency not downloaded: " + dep.getFileName());
        }

        return newDownloaded;
    }
}
