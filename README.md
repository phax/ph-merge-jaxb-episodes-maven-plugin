# ph-merge-jaxb-episodes-maven-plugin

A specific Maven plugin that merges multiple `META-INF/sun-jaxb.episode` files into a single one.

This plugin is helpful if you have multiple executions of the `maven-jaxb2-plugin` in your plugin.
By default only one `META-INF/sun-jaxb.episode` file makes it into the binary representation of the library.

To change this, include the plugin like this (replacing `x.y.z` with the effective version number):

```xml
      <plugin>
        <groupId>com.helger.maven</groupId>
        <artifactId>ph-merge-jaxb-episodes-maven-plugin</artifactId>
        <version>x.y.z</version>
        <executions>
          <execution>
            <phase>generate-resources</phase>
            <goals>
              <goal>merge-jaxb-episodes</goal>
            </goals>
            <configuration>
              <verbose>false</verbose>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

## Parameters:

* **`verbose`** (boolean) - if `true`, more output is written to the log. This is mainly intended to find errors more easily

# News and noteworthy

* v0.0.1 - 2021-08-05
    * Initial release

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
On Twitter: <a href="https://twitter.com/philiphelger">@philiphelger</a> |
Kindly supported by [YourKit Java Profiler](https://www.yourkit.com)
