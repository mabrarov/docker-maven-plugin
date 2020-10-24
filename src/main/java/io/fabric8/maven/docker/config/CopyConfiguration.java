package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

public class CopyConfiguration implements Serializable {

    public static class Entry {

        /**
         * Full path to container file or container directory which needs to copied.
         */
        private String containerPath;

        /**
         * Path to a host directory where the files copied from container need to be placed.
         * If relative path is provided then project base directory is considered as a base of that relative path.
         * Can be <code>null</code> meaning the same as empty string.
         * Note that if containerPath points to a directory, then a directory with the same name will be created in
         * the hostDirectory, i.e. not just content of directory is copied, but the same name directory is created too.
         */
        private String hostDirectory;

        public String getContainerPath() {
            return containerPath;
        }

        public String getHostDirectory() {
            return hostDirectory;
        }
    }

    /**
     * Items to copy from container.
     */
    @Parameter
    private List<Entry> entries;

    public List<Entry> getEntries() {
        return entries;
    }
}
