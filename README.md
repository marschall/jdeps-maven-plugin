Maven jdeps Plugin [![Build Status](https://travis-ci.org/marschall/jdeps-maven-plugin.svg?branch=master)](https://travis-ci.org/marschall/jdeps-maven-plugin)
==================

Maven plugin that runs the Java 8 `jdeps` tool.

For more information check out the generated [plugin page](http://marschall.github.io/jdeps-maven-plugin/) and the [jdeps documentation](http://docs.oracle.com/javase/8/docs/technotes/tools/unix/jdeps.html).

Usage
-----
```xml
  <build>
    <plugins>
      <plugin>
        <groupId>com.github.marschall</groupId>
        <artifactId>jdeps-maven-plugin</artifactId>
        <version>0.4.0</version>
        <!-- optionally any configuration -->
        <configuration>
          <profile>true</profile>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

Then you can run it with:

```
mvn jdeps:jdeps
```

A sample output will look like this:
```
   com.github.marschall.memoryfilesystem (classes)
      -> java.io                                            compact1
      -> java.lang                                          compact1
      -> java.lang.reflect                                  compact1
      -> java.net                                           compact1
      -> java.nio                                           compact1
      -> java.nio.channels                                  compact1
      -> java.nio.file                                      compact1
      -> java.nio.file.attribute                            compact1
      -> java.nio.file.spi                                  compact1
      -> java.text                                          compact1
      -> java.util                                          compact1
      -> java.util.concurrent                               compact1
      -> java.util.concurrent.atomic                        compact1
      -> java.util.concurrent.locks                         compact1
      -> java.util.regex                                    compact1
      -> javax.annotation                                   Full JRE
```

Alternatively you can run the plugin as a reporting plugin. `target/site/jdeps-report.html`

```xml
  <reporting>
    <plugins>
      <plugin>
        <groupId>com.github.marschall</groupId>
        <artifactId>jdeps-maven-plugin</artifactId>
        <version>0.4.0</version>
        <!-- optionally any configuration -->
        <configuration>
          <profile>true</profile>
        </configuration>
      </plugin>
    </plugins>
  </reporting>
```


Options
-------
Early versions of jdeps may not support all options.

```
summary                 Print dependency summary only
verbose                 Print additional information
verbose-level=<level>   Print package-level or class-level dependencies
                        Valid levels are: "package" and "class"
package=<pkg name>      Restrict analysis to classes in this package
                        (may be given multiple times)
regex=<regex>           Restrict analysis to packages matching pattern
                        (package and regex are exclusive)
profile                 Show profile or the file containing a package
recursive               Recursively traverse all dependencies
version                 Version information
jdkinternals            Finds class-level dependences in JDK internal
                        APIs. By default, it analyzes all classes
                        specified in the -classpath option and in input
                        files unless you specified the include option.
                        You cannot use this option with the package,
                        regex, and summary options.
dotOutputDirectory      Destination directory for DOT file output.
                        Default ${project.reporting.outputDirectory}/jdeps
include                 Restricts analysis to classes matching pattern.
apionly                 Restricts analysis to APIs.
```

Building
-------
You need to have a ~/.m2/[toolchains.xml](http://maven.apache.org/guides/mini/guide-using-toolchains.html) that contains a JDK 1.7 link.

