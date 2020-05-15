package dev.nicho.dependencymanager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DependencyManager {

    final int CONNECT_TIMEOUT = 4000;
    final int READ_TIMEOUT = 10000;

    private List<Dependency> dependencies = null;
    private File libFolder = null;

    public DependencyManager(File libFolder) {
        if (!libFolder.isDirectory()) {
            throw new IllegalArgumentException("libFolder must be a directory");
        }

        this.libFolder = libFolder;

        dependencies = new ArrayList<Dependency>();
    }

    public void addDependency(URL url) {
        dependencies.add(new Dependency(url));
    }

    public void addDependency(URL url, String fileName) {
        dependencies.add(new Dependency(url, fileName));
    }

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
