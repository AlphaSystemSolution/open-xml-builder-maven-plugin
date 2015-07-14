/**
 *
 */
package com.alphasystem.openxml.mavenplugin;

import com.sun.codemodel.JCodeModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;

/**
 * @author sali
 */
@Mojo(name = "generate", requiresProject = true, defaultPhase = GENERATE_SOURCES)
@Execute(goal = "generate", phase = GENERATE_SOURCES, lifecycle = "generate-sources")
public class OpenXmlFluentApiBuilder extends AbstractMojo {

    @Parameter(name = "targetDirectory", required = true, defaultValue = "${project.build.directory}/generated-sources/openxml")
    private File targetDirectory;

    @Parameter(name = "srcClassNames")
    private List<String> srcClassNames;

    private Class<?>[] srcClasses;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }

        initClasses();
        generate();
    }

    private void generate() {
        JCodeModel codeModel = new JCodeModel();
        FluentApiGenerator apiGenerator = new FluentApiGenerator(codeModel,
                srcClasses);
        apiGenerator.generate();
        try {
            codeModel.build(targetDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Class<?>[] getSrcClasses() {
        return srcClasses;
    }

    public void setSrcClasses(Class<?>[] srcClasses) {
        this.srcClasses = srcClasses;
    }

    public List<String> getSrcClassNames() {
        return srcClassNames;
    }

    public void setSrcClassNames(List<String> srcClasses) {
        this.srcClassNames = srcClasses;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    private void initClasses() {
        srcClasses = new Class<?>[srcClassNames.size()];
        for (int i = 0; i < srcClassNames.size(); i++) {
            try {
                Class<?> _class = Class.forName(srcClassNames.get(i));
                srcClasses[i] = _class;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

}
