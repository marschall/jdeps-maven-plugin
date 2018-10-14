package com.github.marschall.jdeps;

import static org.apache.maven.plugins.annotations.LifecyclePhase.SITE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Puts the output of the jdpes tool into a report page.
 *
 * @author Philippe Marschall
 */
@Mojo(name = "jdeps-report",
  threadSafe = true,
  requiresProject = true,
  defaultPhase = SITE,
  requiresDependencyResolution = COMPILE
)
public class JdepsReportMojo extends AbstractMavenReport {

  @Component
  private ToolchainManager toolchainManager;

  @Parameter(defaultValue = "${project}", readonly = true) // @Component is deprecated
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true) // @Component is deprecated
  private MavenSession session;

  @Component
  private Renderer siteRenderer;

  /**
   * Print dependency summary only.
   */
  @Parameter(defaultValue = "false", property = "jdeps.summary")
  private boolean summary;

  /**
   * Finds class-level dependences on JDK internal APIs.
   *
   * By default, it analyzes all classes and input files unless
   * "include" option is specified. This option cannot be used with
   * "packages", "regex" and "summary" options.
   * <p>
   * <strong>WARNING:</strong> JDK internal APIs may not be accessible
   * in the next release.
   */
  @Parameter(defaultValue = "false", property = "jdeps.jdkInternals")
  private boolean jdkInternals;

  /**
   * Restrict analysis to APIs.
   *
   * i.e. dependences  from the signature of public and protected
   * members of public classes including field type, method parameter
   * types, returned type, checked exception types etc.
   */
  @Parameter(defaultValue = "false", property = "jdeps.apiOnly")
  private boolean apiOnly;

  /**
   * Print all class level dependencies.
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
   * Filter dependences matching the given pattern.
   */
  @Parameter(property = "jdeps.filter")
  private String filter;

  /**
   * Filter mode. Options
   *
   * <dl>
   *  <dt>package</dt>
   *  <dd>Filter dependences within the same package (default)</dd>
   *  <dt>archive</dt>
   *  <dd>Filter dependences within the same archive</dd>
   *  <dt>none</dt>
   *  <dd>No package and archive filtering. Filtering specified via the filter option still applies.</dd>
   * </dl>
   */
  @Parameter(property = "jdeps.filterMode")
  private String filterMode;

  /**
   * Restricts analysis to classes matching pattern.
   *
   * This option filters the list of classes to be analyzed. It can be
   * used together with "packages" or "regex" which apply pattern to
   * the dependencies.
   */
  @Parameter(property = "jdeps.include")
  private String include;

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

  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.reporting.outputDirectory}")
  private File reportOutputDirectory;

  @Override
  public String getOutputName() {
    return "jdeps-report";
  }

  private ResourceBundle getBundle(Locale locale) {
    return ResourceBundle.getBundle("jdeps-report", locale, this.getClass().getClassLoader());
  }

  @Override
  public String getName(Locale locale) {
    return this.getBundle(locale).getString( "report.jdeps.name" );
  }

  @Override
  public String getDescription(Locale locale) {
    return this.getBundle(locale).getString("report.jdeps.description");
  }

  @Override
  protected void executeReport(Locale locale) throws MavenReportException {
    Sink sink = this.getSink();
    String jExecutable;
    try {
      jExecutable = this.getJdepsExecutable();
    } catch (IOException e ) {
      throw new MavenReportException("Unable to find jdeps command: " + e.getMessage(), e );
    }
    Commandline cmd = this.buildCommandLine(jExecutable);

    String output = this.executeJDepsCommandLine(cmd);

    sink.head();
    sink.title();
    sink.text("JDeps report");
    sink.title_();
    sink.head_();

    sink.body();
    sink.section1();

    sink.sectionTitle1();
    sink.text("JDeps report");
    sink.sectionTitle1_();

    sink.verbatim(true);
    sink.text(output);
    sink.verbatim_();

    sink.section1_();
    sink.body_();
    sink.flush();
    sink.close();
  }

  private Commandline buildCommandLine(String jExecutable) throws MavenReportException {
    Commandline cmd = new Commandline();
    cmd.setExecutable(jExecutable);

    this.addApiOnly(cmd);
    this.addClassPathArg(cmd);
    this.addInclude(cmd);
    this.addJdkinternals(cmd);
    this.addPackagesArg(cmd);
    this.addProfileArg(cmd);
    this.addRegexArg(cmd);
    this.addRecursiveArg(cmd);
    this.addSummaryArg(cmd);
    this.addVerboseArg(cmd);
    this.addVerboseLevelArg(cmd);
    this.addFilterArg(cmd);
    this.addFilterModeArg(cmd);
    this.addVersionArg(cmd);

    this.addOutputArg(cmd);
    return cmd;
  }

  private void addPackagesArg(Commandline cmd) {
    if ((this.packages != null) && !this.packages.isEmpty()) {
      for (String each : this.packages) {
        cmd.createArg().setValue("-package");
        cmd.createArg().setValue(each);
      }
    }
  }

  private void addClassPathArg(Commandline cmd) throws MavenReportException {
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
        throw new MavenReportException("Can't read path separator");

      }
      if (!fileNames.isEmpty()) {
        // jdeps doesn't like an empty classpath
        String classPath = StringUtils.join(fileNames.iterator(), pathSeparator);
        cmd.createArg().setValue("-classpath");
        cmd.createArg().setValue(classPath);
      }
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

  private void addInclude(Commandline cmd) {
    if (this.include != null) {
      cmd.createArg().setValue("-regex");
      cmd.createArg().setValue(this.include);
    }
  }

  private void addVerboseArg(Commandline cmd) {
    this.addBooleanArg(this.verbose, "-verbose", cmd);
  }

  private void addSummaryArg(Commandline cmd) {
    this.addBooleanArg(this.summary, "-summary", cmd);
  }

  private void addJdkinternals(Commandline cmd) {
    this.addBooleanArg(this.jdkInternals, "-jdkinternals", cmd);
  }

  private void addApiOnly(Commandline cmd) {
    this.addBooleanArg(this.apiOnly, "-apionly", cmd);
  }

  private void addProfileArg(Commandline cmd) {
    this.addBooleanArg(this.profile, "-profile", cmd);
  }

  private void addRecursiveArg(Commandline cmd) {
    this.addBooleanArg(this.recursive, "-recursive", cmd);
  }


  private void addFilterArg(Commandline cmd) {
    if (this.filter != null) {
      cmd.createArg().setValue("-filter");
      cmd.createArg().setValue(this.filter);
    }
  }

  private void addFilterModeArg(Commandline cmd) {
    if (this.filterMode != null) {
      switch (this.filterMode) {
        case "package":
          cmd.createArg().setValue("-filter:package");
          break;
        case "archive":
          cmd.createArg().setValue("-filter:archive");
          break;
        case "none":
          cmd.createArg().setValue("-filter:none");
          break;
        default:
          // throw an exception?
          break;
      }
    }
  }

  private void addVersionArg(Commandline cmd) {
    this.addBooleanArg(this.version, "-version", cmd);
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
  private String executeJDepsCommandLine(Commandline cmd) throws MavenReportException {

    StringStreamConsumer err = new StringStreamConsumer();
    StringStreamConsumer out = new StringStreamConsumer();
    try {
      int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

      if (exitCode == 0) {
        return out.getOutput();
      } else {
        String cmdLine = CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", "");

        StringBuilder msg = new StringBuilder("\nExit code: ");
        msg.append(exitCode);
        if (StringUtils.isNotEmpty(err.getOutput())) {
          msg.append(" - ").append(err.getOutput());
        }
        msg.append('\n');
        msg.append("Command line was: ").append(cmdLine).append('\n').append('\n');

        throw new MavenReportException(msg.toString());
      }
    } catch (CommandLineException e) {
      throw new MavenReportException("Unable to execute jdeps command: " + e.getMessage(), e);
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
    JDepsCommandUtil jdepsCommandUtil = new JDepsCommandUtil(this.toolchainManager, this.getLog(), this.session);
    return jdepsCommandUtil.getJdepsExecutable();
  }

  @Override
  protected String getOutputDirectory() {
    return this.reportOutputDirectory.getAbsolutePath();
  }

  @Override
  protected MavenProject getProject() {
    return this.project;
  }

  @Override
  protected Renderer getSiteRenderer() {
    return this.siteRenderer;
  }

}
