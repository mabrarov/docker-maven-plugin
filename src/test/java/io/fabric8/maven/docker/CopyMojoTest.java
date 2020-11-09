package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Tested;
import mockit.Verifications;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.RunService.ContainerDescriptor;
import io.fabric8.maven.docker.util.GavLabel;

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_START_CALLED;

public class CopyMojoTest extends BaseMojoTest {

    @Tested(fullyInitialized = false)
    private CopyMojo copyMojo;

    @Test
    public void copyWithCreateContainersButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithCreateContainersButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithCreateContainersButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithStartGoalInvokedButNoContainersTracked() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenNoContainersTracked();

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithStartGoalInvokedButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenContainerTracked(singleContainerDescriptor(singleImageWithBuild()));

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithStartGoalInvokedButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenContainerTracked(singleContainerDescriptor(singleImageWithCopy(Collections.emptyList())));

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));

        whenMojoExecutes();

        thenNoContainerLookupOccurs();
        thenCopyArchiveFromContainerIsNotCalled();
    }


    private void givenProjectWithStartGoalInvoked() {
        givenMavenProject(copyMojo);
        givenPluginContext(copyMojo, CONTEXT_KEY_START_CALLED, true);
    }

    private void givenMavenProject() {
        givenMavenProject(copyMojo);
    }

    private void givenProjectWithResolvedImage(ImageConfiguration image) {
        givenMavenProject(copyMojo);
        givenResolvedImages(copyMojo, Collections.singletonList(image));
    }

    private void givenCreateContainersIsTrue() {
        Deencapsulation.setField(copyMojo, "createContainers", true);
    }

    private void givenNoContainersTracked() {
        new Expectations() {{
            runService.getContainers((GavLabel) any);
            result = Collections.emptyList();
            minTimes = 0;
        }};
    }

    private void givenContainerTracked(ContainerDescriptor container) {
        new Expectations() {{
            runService.getContainers((GavLabel) any);
            result = Collections.singletonList(container);
            minTimes = 0;
        }};
    }

    private ContainerDescriptor singleContainerDescriptor(ImageConfiguration imageConfiguration) {
        return new ContainerDescriptor("example", imageConfiguration);
    }

    private void whenMojoExecutes() throws IOException, MojoExecutionException {
        copyMojo.executeInternal(serviceHub);
    }

    private void thenNoContainerLookupOccurs() throws DockerAccessException {
        thenListContainersIsNotCalled();
        thenGetLatestContainerIsNotCalled();
        thenNoContainerLookupByImageOccurs();
        thenNoLatestContainerLookupByImageOccurs();
    }

    private void thenListContainersIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.listContainers(anyBoolean);
            times = 0;
        }};
    }

    private void thenGetLatestContainerIsNotCalled() {
        new Verifications() {{
            //noinspection unchecked
            queryService.getLatestContainer((List<Container>) any);
            times = 0;
        }};
    }

    private void thenNoContainerLookupByImageOccurs() throws DockerAccessException {
        new Verifications() {{
            queryService.getContainersForImage(anyString, anyBoolean);
            times = 0;
        }};
    }

    private void thenNoLatestContainerLookupByImageOccurs() throws DockerAccessException {
        new Verifications() {{
            queryService.getLatestContainerForImage(anyString, anyBoolean);
            times = 0;
        }};
    }

    private void thenNoContainerIsCreated() throws DockerAccessException {
        new Verifications() {{
            //noinspection ConstantConditions
            runService.createContainer((ImageConfiguration) any, (PortMapping) any, (GavLabel) any, (Properties) any,
                    (File) any, anyString, (Date) any);
            times = 0;
        }};
    }

    private void thenCopyArchiveFromContainerIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            dockerAccess.copyArchiveFromContainer(anyString, anyString, (File) any);
            times = 0;
        }};
    }
}
