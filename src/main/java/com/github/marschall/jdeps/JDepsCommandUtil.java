package com.github.marschall.jdeps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

final class JDepsCommandUtil {

  private final ToolchainManager toolchainManager;
  private final Log log;
  private final MavenSession session;

  JDepsCommandUtil(ToolchainManager toolchainManager, Log log, MavenSession session) {
    this.toolchainManager = toolchainManager;
    this.log = log;
    this.session = session;
  }

  /**
   * Get the path of the JDeps tool executable depending the user entry or try to find it depending the OS
   * or the <code>java.home</code> system property or the <code>JAVA_HOME</code> environment variable.
   *
   * @return the path of the JDeps tool
   * @throws IOException if not found
   */
  String getJdepsExecutable() throws IOException {
    String jdepsExecutable = null;
    Toolchain toolchain = this.toolchainManager.getToolchainFromBuildContext("jdk", this.session);

    if (toolchain != null) {
      this.log.info("Toolchain in jdeps-maven-plugin: " + toolchain);
      jdepsExecutable = toolchain.findTool("jdeps");
    }
    String jdepsCommand = "jdeps" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");

    Path jdepsExe;

    // ----------------------------------------------------------------------
    // The jdeps executable is defined by the user
    // ----------------------------------------------------------------------
    if (StringUtils.isNotEmpty(jdepsExecutable)) {
      jdepsExe = Paths.get(jdepsExecutable);

      if (Files.isDirectory(jdepsExe)){
        jdepsExe = jdepsExe.resolve(jdepsCommand);
      }

      String fileName = jdepsExe.getFileName().toString();
      if (SystemUtils.IS_OS_WINDOWS && (fileName.indexOf('.') < 0)) {
        jdepsExe = jdepsExe.resolveSibling(fileName + ".exe");
      }

      if (!Files.isRegularFile(jdepsExe)) {
        throw new IOException("The jdeps executable '" + jdepsExe + "' doesn't exist or is not a file.");
      }

      return jdepsExe.toAbsolutePath().toString();
    }

    // Try to find jdepsExe from System.getProperty( "java.home" )
    // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
    // should be in the JDK_HOME
    Path javaHomePath = Paths.get(SystemUtils.getJavaHome().toURI());
    jdepsExe = tryResolveJdeps(javaHomePath, jdepsCommand);
    if (jdepsExe == null) {
      // If Java home is actually a JRE see maybe its a JRE inside a JDK
      jdepsExe = tryResolveJdeps(javaHomePath.getParent(), jdepsCommand);
    }

    // Try to find jdepsExe from JAVA_HOME environment variable
    if (jdepsExe == null) {
      Properties env = CommandLineUtils.getSystemEnvVars();
      String javaHome = env.getProperty("JAVA_HOME");
      if (StringUtils.isEmpty(javaHome)) {
        throw new IOException( "The environment variable JAVA_HOME is not correctly set." );
      }
      javaHomePath = Paths.get(javaHome);
      if (!Files.exists(javaHomePath) || !Files.isDirectory(javaHomePath)) {
        throw new IOException("The environment variable JAVA_HOME=" + javaHome + " doesn't exist or is not a valid directory.");
      }
      jdepsExe = tryResolveJdeps(javaHomePath, jdepsCommand);
    }

    if (jdepsExe == null) {
      throw new IOException("The jdeps executable '" + jdepsExe + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
    }

    return jdepsExe.toAbsolutePath().toString();
  }

  private static Path tryResolveJdeps(Path javaHome, String jdepsCommand) {
    Path javaHomeBin = javaHome.resolve("bin");
    if (Files.exists(javaHomeBin) && Files.isDirectory(javaHomeBin)) {
      Path resolved = javaHomeBin.resolve(jdepsCommand);
      if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
        return resolved;
      }
    }
    return null;
  }

}
