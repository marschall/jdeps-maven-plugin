package com.github.marschall.jdeps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Arg;
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
  requiresDependencyCollection = ResolutionScope.COMPILE)
public class JDepsMojo extends AbstractMojo {

  @Component
  private Toolchain toolChain;
  
  @Component
  private MavenProject project;
  
  /**
   * Print dependency summary only.
   */
  @Parameter(defaultValue = "false")
  private boolean summary;
  
  /**
   * Print additional information.
   */
  @Parameter(defaultValue = "false")
  private boolean verbose;
  
  /**
   * Print package-level or class-level dependencies
   * Valid levels are: "package" and "class".
   */
  @Parameter(alias = "verbose-level")
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
  @Parameter
  private String regex;
  
  /**
   * Show profile or the file containing a package.
   */
  @Parameter(defaultValue = "false")
  private boolean profile;
  
  /**
   * Recursively traverse all dependencies.
   */
  @Parameter(defaultValue = "false")
  private boolean recursive;
  
  /**
   * Version information.
   */
  @Parameter(defaultValue = "false")
  private boolean version;
  
  @Parameter(defaultValue = "${project.outputDirectory}", readonly = true)
  private String outputDirectory;

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
        Arg packageArg = cmd.createArg();
        packageArg.setValue("--package=" + each);
        cmd.addArg(packageArg);
      }
    }
  }

  private void addClassPathArg(Commandline cmd) throws MojoFailureException {
    try {
      List<String> classpathElements = this.project.getCompileClasspathElements();
      if (!classpathElements.isEmpty()) {
        String pathSeparator = SystemUtils.PATH_SEPARATOR;
        if (pathSeparator == null) {
          throw new MojoFailureException("Can't read path separator");
          
        }
        String classPath = StringUtils.join(classpathElements.iterator(), pathSeparator);
        Arg classPathArg = cmd.createArg();
        classPathArg.setValue("--classpath=" + classPath);
        cmd.addArg(classPathArg);
        
      }
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoFailureException("Dependency resolution required", e );
    }
  }

  private void addVerboseLevelArg(Commandline cmd) {
    if (this.verboseLevel != null) {
      Arg verboseLevelArg = cmd.createArg();
      verboseLevelArg.setValue("--verbose-level=" + this.verboseLevel);
      cmd.addArg(verboseLevelArg);
    }
  }
  
  private void addRegexArg(Commandline cmd) {
    if (this.regex != null) {
      Arg regexArg = cmd.createArg();
      regexArg.setValue("--regex=" + this.regex);
      cmd.addArg(regexArg);
    }
  }

  private void addVerboseArg(Commandline cmd) {
    addBooleanArg(this.verbose, "--verbose", cmd);
  }

  private void addSummaryArg(Commandline cmd) {
    addBooleanArg(this.summary, "--summary", cmd);
  }
  
  private void addProfileArg(Commandline cmd) {
    addBooleanArg(this.profile, "--profile", cmd);
  }
  
  private void addRecursiveArg(Commandline cmd) {
    addBooleanArg(this.recursive, "--recursive", cmd);
  }
  
  private void addVersionArg(Commandline cmd) {
    addBooleanArg(this.version, "--version", cmd);
  }
  
  private void addBooleanArg(boolean flag, String name, Commandline cmd) {
    if (flag) {
      Arg arg = cmd.createArg();
      arg.setValue(name);
      cmd.addArg(arg);
    }
  }
  
  private void addOutputArg(Commandline cmd) {
    Arg arg = cmd.createArg();
    arg.setValue(this.outputDirectory);
    cmd.addArg(arg);
  }

  /**
   * Execute the JDeps command line
   *
   * @param cmd not null
   * @throws MavenReportException if any errors occur
   */
  private void executeJDepsCommandLine(Commandline cmd) throws MojoFailureException {

    String cmdLine = null;

    CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();
    CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();
    try {
      int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

      String output = StringUtils.isEmpty( out.getOutput() ) ? null : '\n' + out.getOutput().trim();
      if ( StringUtils.isNotEmpty( output ) ) {
        getLog().info( output );
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

      if (StringUtils.isNotEmpty(output)) {
        getLog().info(output);
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
    String jdepsExecutable = toolChain.findTool("jdeps");

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
      throw new IOException( "The jdepts executable '" + jdepsExe + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable." );
    }

    return jdepsExe.toAbsolutePath().toString();
  }


}
