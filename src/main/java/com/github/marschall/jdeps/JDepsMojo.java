package com.github.marschall.jdeps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Runs the jdeps tool.
 * 
 * @author Philippe Marschall
 */
@Mojo(name = "jdeps",
  threadSafe = true,
  requiresProject = true,
  defaultPhase = LifecyclePhase.VERIFY,
  requiresDependencyResolution = ResolutionScope.COMPILE)
public class JDepsMojo extends AbstractMojo {

  @Component
  private ToolchainManager toolchainManager;
  
  @Component
  private MavenProject project;
  
  @Component
  private MavenSession session;
  
  /**
   * Print dependency summary only.
   */
  @Parameter(defaultValue = "false", property = "jdeps.summary")
  private boolean summary;
  
  /**
   * Print additional information.
   */
  @Parameter(defaultValue = "false", property = "jdeps.verbose")
  private boolean verbose;
  
  /**
   * Print package-level or class-level dependencies
   * Valid levels are: "package" and "class".
   */
  @Parameter(alias = "verbose-level", property = "jdeps.verboseLevel")
  private String verboseLevel;
  
  /**
   * Restrict analysis to classes in these packages.
   */
  @Parameter
  private List<String> packages;
  
  /**
   * Restrict analysis to packages matching pattern.
   * ("packages" and "regex" are exclusive)
   */
  @Parameter(property = "jdeps.regex")
  private String regex;
  
  /**
   * Show profile or the file containing a package.
   */
  @Parameter(defaultValue = "false", property = "jdeps.profile")
  private boolean profile;
  
  /**
   * Recursively traverse all dependencies.
   */
  @Parameter(defaultValue = "false", property = "jdeps.recursive")
  private boolean recursive;
  
  /**
   * Version information.
   */
  @Parameter(defaultValue = "false", property = "jdeps.version")
  private boolean version;
  
  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, property = "jdeps.outputDirectory")
  private File outputDirectory;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    String jExecutable;
    try {
      jExecutable = getJdepsExecutable();
    } catch ( IOException e ) {
      throw new MojoFailureException("Unable to find jdeps command: " + e.getMessage(), e );
    }
    Commandline cmd = new Commandline();
    cmd.setExecutable(jExecutable);
    
    addSummaryArg(cmd);
    addVerboseArg(cmd);
    addVerboseLevelArg(cmd);
    addClassPathArg(cmd);
    addPackagesArg(cmd);
    addRegexArg(cmd);
    addProfileArg(cmd);
    addRecursiveArg(cmd);
    addVersionArg(cmd);
    addOutputArg(cmd);

    this.executeJDepsCommandLine(cmd);
  }

  private void addPackagesArg(Commandline cmd) {
    if (this.packages != null && !this.packages.isEmpty()) {
      for (String each : this.packages) {
        cmd.createArg().setValue("-package");
        cmd.createArg().setValue(each);
      }
    }
  }

  private void addClassPathArg(Commandline cmd) throws MojoFailureException {
    Set<Artifact> dependencyArtifacts = this.project.getDependencyArtifacts();
    if (!dependencyArtifacts.isEmpty()) {
      List<String> fileNames = new ArrayList<>(dependencyArtifacts.size());
      for (Artifact artifact : dependencyArtifacts) {
        File file = artifact.getFile();
        if (file != null) {
          fileNames.add(file.getAbsolutePath());
        }
      }
      String pathSeparator = System.getProperty("path.separator");
      if (pathSeparator == null) {
        throw new MojoFailureException("Can't read path separator");

      }
      String classPath = StringUtils.join(fileNames.iterator(), pathSeparator);
      cmd.createArg().setValue("-classpath");
      cmd.createArg().setValue(classPath);
    }
  }

  private void addVerboseLevelArg(Commandline cmd) {
    if (this.verboseLevel != null) {
      cmd.createArg().setValue("-verbose:" + this.verboseLevel);
    }
  }
  
  private void addRegexArg(Commandline cmd) {
    if (this.regex != null) {
      cmd.createArg().setValue("-regex");
      cmd.createArg().setValue(this.regex);
    }
  }

  private void addVerboseArg(Commandline cmd) {
    addBooleanArg(this.verbose, "-verbose", cmd);
  }

  private void addSummaryArg(Commandline cmd) {
    addBooleanArg(this.summary, "-summary", cmd);
  }
  
  private void addProfileArg(Commandline cmd) {
    addBooleanArg(this.profile, "-profile", cmd);
  }
  
  private void addRecursiveArg(Commandline cmd) {
    addBooleanArg(this.recursive, "-recursive", cmd);
  }
  
  private void addVersionArg(Commandline cmd) {
    addBooleanArg(this.version, "-version", cmd);
  }
  
  private void addBooleanArg(boolean flag, String name, Commandline cmd) {
    if (flag) {
      cmd.createArg().setValue(name);
    }
  }
  
  private void addOutputArg(Commandline cmd) {
    cmd.createArg().setFile(this.outputDirectory);
  }

  /**
   * Execute the JDeps command line
   *
   * @param cmd not null
   * @throws MojoFailureException if any errors occur
   */
  private void executeJDepsCommandLine(Commandline cmd) throws MojoFailureException {

    String cmdLine = null;

    CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
    CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
    try {
      int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

      String output = StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim();
      if (StringUtils.isNotEmpty(output)) {
        getLog().info(output);
      }

      if (exitCode != 0) {
        cmdLine = CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", "");


        StringBuilder msg = new StringBuilder("\nExit code: ");
        msg.append(exitCode);
        if (StringUtils.isNotEmpty(err.getOutput())) {
          msg.append(" - ").append(err.getOutput());
        }
        msg.append('\n');
        msg.append("Command line was: ").append(cmdLine).append('\n').append('\n');

        throw new MojoFailureException(msg.toString());
      }
    }
    catch (CommandLineException e) {
      throw new MojoFailureException("Unable to execute jdeps command: " + e.getMessage(), e);
    }
  }

  /**
   * Get the path of the JDeps tool executable depending the user entry or try to find it depending the OS
   * or the <code>java.home</code> system property or the <code>JAVA_HOME</code> environment variable.
   *
   * @return the path of the JDeps tool
   * @throws IOException if not found
   */
  private String getJdepsExecutable() throws IOException {
    String jdepsExecutable = null;
    Toolchain toolchain = this.toolchainManager.getToolchainFromBuildContext("jdk", this.session);
    
    if (toolchain != null) {
      getLog().info("Toolchain in jdeps-maven-plugin: " + toolchain);
      jdepsExecutable = toolchain.findTool("jdeps");
    }
    String jdepsCommand = "jdeps" + ( SystemUtils.IS_OS_WINDOWS ? ".exe" : "" );

    Path jdepsExe;

    // ----------------------------------------------------------------------
    // The jdps executable is defined by the user
    // ----------------------------------------------------------------------
    if (StringUtils.isNotEmpty(jdepsExecutable)) {
      jdepsExe = Paths.get(jdepsExecutable);

      if (Files.isDirectory(jdepsExe)){
        jdepsExe = jdepsExe.resolve(jdepsCommand);
      }

      String fileName = jdepsExe.getFileName().toString();
      if (SystemUtils.IS_OS_WINDOWS && fileName.indexOf('.') < 0) {
        jdepsExe = jdepsExe.resolveSibling(fileName + ".exe");
      }

      if (!Files.isRegularFile(jdepsExe)) {
        throw new IOException( "The jdeps executable '" + jdepsExe + "' doesn't exist or is not a file." );
      }

      return jdepsExe.toAbsolutePath().toString();
    }

    // ----------------------------------------------------------------------
    // Try to find jdepsExe from System.getProperty( "java.home" )
    // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
    // should be in the JDK_HOME
    // ----------------------------------------------------------------------
    if ( SystemUtils.IS_OS_MAC_OSX ) {
      jdepsExe = Paths.get(SystemUtils.getJavaHome().toURI()).resolve("bin").resolve(jdepsCommand);
    } else {
      jdepsExe = Paths.get(SystemUtils.getJavaHome().toURI()).getParent().resolve("bin").resolve(jdepsCommand);
    }

    // ----------------------------------------------------------------------
    // Try to find jdepsExe from JAVA_HOME environment variable
    // ----------------------------------------------------------------------
    if (!Files.exists(jdepsExe) || !Files.isRegularFile(jdepsExe)) {
      Properties env = CommandLineUtils.getSystemEnvVars();
      String javaHome = env.getProperty("JAVA_HOME");
      if (StringUtils.isEmpty(javaHome)) {
        throw new IOException( "The environment variable JAVA_HOME is not correctly set." );
      }
      Path javaHomePath = Paths.get(javaHome);
      if (!Files.exists(jdepsExe) || !Files.isDirectory(jdepsExe)) {
        throw new IOException("The environment variable JAVA_HOME=" + javaHome + " doesn't exist or is not a valid directory." );
      }

      jdepsExe = javaHomePath.resolve("bin").resolve(jdepsCommand);
    }

    if (!Files.exists(jdepsExe) || !Files.isRegularFile(jdepsExe)) {
      throw new IOException( "The jdeps executable '" + jdepsExe + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable." );
    }

    return jdepsExe.toAbsolutePath().toString();
  }


}
