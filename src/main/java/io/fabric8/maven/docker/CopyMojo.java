package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.CopyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ArchiveService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.RunService.ContainerDescriptor;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.Logger;

/**
 * Mojo for copying file or directory from container.
 *
 * If called together with <code>docker:start</code> (i.e. when configured for integration testing in a lifecycle
 * phase), then only the containers started by that goal are examined.
 *
 * If this goal is called standalone, then all images which are configured in pom.xml are iterated. For each image a
 * temporary container is created (but not started) before the copying and is removed after completion of the copying
 * (even if the copying failed).
 */
@Mojo(name = "copy", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class CopyMojo extends AbstractDockerMojo {

    private static final String TEMP_ARCHIVE_FILE_PREFIX = "docker-copy-";
    private static final String TEMP_ARCHIVE_FILE_SUFFIX = ".tar";

    /**
     * Naming pattern for how to name containers when created
     */
    @Parameter(property = "docker.containerNamePattern")
    private String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    @Override
    protected void executeInternal(ServiceHub hub) throws IOException, MojoExecutionException {
        DockerAccess dockerAccess = hub.getDockerAccess();
        ArchiveService archiveService = hub.getArchiveService();
        RunService runService = hub.getRunService();
        GavLabel gavLabel = getGavLabel();

        if (invokedTogetherWithDockerStart()) {
            log.debug("Mojo is invoked together with start");
            List<ContainerDescriptor> containerDescriptors = runService.getContainers(gavLabel);
            for (ContainerDescriptor containerDescriptor : containerDescriptors) {
                String containerId = containerDescriptor.getContainerId();
                ImageConfiguration imageConfiguration = containerDescriptor.getImageConfig();
                String imageName = imageConfiguration.getName();
                log.debug("Found %s container of %s image", containerId, imageName);
                CopyConfiguration copyConfiguration = imageConfiguration.getCopyConfiguration();
                copy(dockerAccess, archiveService, containerId, imageName, copyConfiguration);
            }
        } else {
            log.debug("Mojo is invoked standalone, will create temporary containers");
            List<ImageConfiguration> imageConfigurations = getResolvedImages();
            for (ImageConfiguration imageConfiguration : imageConfigurations) {
                String imageName = imageConfiguration.getName();
                try (ContainerRemover containerRemover = new ContainerRemover(log, runService, removeVolumes)) {
                    String containerId = createContainer(runService, imageConfiguration, gavLabel);
                    containerRemover.setContainerId(containerId);
                    log.debug("Created %s container from %s image", containerId, imageName);
                    CopyConfiguration copyConfiguration = imageConfiguration.getCopyConfiguration();
                    copy(dockerAccess, archiveService, containerId, imageName, copyConfiguration);
                }
            }
        }
    }

    private String createContainer(RunService runService, ImageConfiguration imageConfiguration, GavLabel gavLabel)
            throws IOException {
        Properties projectProperties = project.getProperties();
        return runService.createContainer(imageConfiguration,
                runService.createPortMapping(imageConfiguration.getRunConfiguration(), projectProperties), gavLabel,
                projectProperties, project.getBasedir(), containerNamePattern, getBuildTimestamp());
    }

    private void copy(DockerAccess dockerAccess, ArchiveService archiveService, String containerId, String imageName,
            CopyConfiguration copyConfiguration) throws IOException, MojoExecutionException {
        if (containerId == null || copyConfiguration == null) {
            return;
        }
        List<CopyConfiguration.Entry> entries = copyConfiguration.getEntries();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (CopyConfiguration.Entry entry : entries) {
            String containerPath = entry.getContainerPath();
            if (containerPath == null) {
                log.error("containerPath of containerToHostCopyEntry of %s container of %s image is not specified",
                        containerId, imageName);
                throw new IllegalArgumentException("containerPath should be specified");
            }
            File hostDirectory = getHostDirectory(entry.getHostDirectory());
            Files.createDirectories(hostDirectory.toPath());
            try (FileRemover fileRemover = new FileRemover(log)) {
                File archiveFile = Files.createTempFile(TEMP_ARCHIVE_FILE_PREFIX, TEMP_ARCHIVE_FILE_SUFFIX).toFile();
                fileRemover.setFile(archiveFile);
                log.debug("Created %s temporary file for docker copy archive", archiveFile);
                log.debug("Copying %s from %s container into %s host file", containerPath, containerId, archiveFile);
                dockerAccess.copyArchiveFromContainer(containerId, containerPath, archiveFile);
                log.debug("Extracting %s archive into %s directory", archiveFile, hostDirectory);
                archiveService.extractDockerCopyArchive(archiveFile, hostDirectory);
            }
        }
    }

    private File getHostDirectory(String hostPath) {
        File projectBaseDirectory = project.getBasedir();
        if (hostPath == null) {
            return projectBaseDirectory;
        }
        File hostDirectory = new File(hostPath);
        if (hostDirectory.isAbsolute()) {
            return hostDirectory;
        }
        return new File(projectBaseDirectory, hostPath);
    }

    private static class ContainerRemover implements AutoCloseable {

        private final Logger logger;
        private final RunService runService;
        private final boolean removeVolumes;
        private String containerId;

        public ContainerRemover(Logger logger, RunService runService, boolean removeVolumes) {
            this.logger = logger;
            this.runService = runService;
            this.removeVolumes = removeVolumes;
        }

        public void setContainerId(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public void close() throws IOException {
            if (containerId != null) {
                logger.debug("Removing %s container", containerId);
                runService.removeContainer(containerId, removeVolumes);
                containerId = null;
            }
        }
    }

    private static class FileRemover implements AutoCloseable {

        private final Logger logger;
        private File file;

        public FileRemover(Logger logger) {
            this.logger = logger;
        }

        public void setFile(File file) {
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                logger.debug("Removing %s file", file);
                Files.delete(file.toPath());
                file = null;
            }
        }
    }
}
