# graal-empty-classpath-reproducer

A reproducer for a bug where Clojure's classpath tools appear to show incorrect
results on JDK11 GraalVM CE.

## Analysis

[c.j.classpath][cpalgo] walks the ClassLoader tree up from the Clojure base
loader:

```clojure
(defn classpath
  ([classloader]
     (distinct
      (mapcat
       loader-classpath
       (take-while
        identity
        (iterate #(.getParent ^ClassLoader %) classloader)))))
  ([]
   (or (seq (classpath (clojure.lang.RT/baseLoader)))
       (system-classpath))))
```

On JDK9+, this no longer works: each ClassLoader will return empty (normally).
This is why the 0-arg implementation says `(or strategy-as-described-above
(system-classpath))`. `system-classpath` checks the `java.class.path` property,
and works correctly on JDK9+.

Normally, this is enough to have c.j.classpath work on JDK8 and above. However,
we were able to create a specific set of circumstances (GraalVM, JDK11, lein
repl with refactor plugin) where an additional classloader was added to that
hierarchy that _does_ have a `loader-classpath`, albeit with only a single value
(the GraalVM src.zip). This confuses cp/classpath into thinking it's in an
environment where it should trust the output from walking the classloader
hierarchy (a situation normally only true on JDK8).

The following minimal command will trigger the bug.

```
echo "(-main)" | lein update-in :dependencies conj \[nrepl\ \"0.7.0\"\] -- update-in :plugins conj \[refactor-nrepl\ \"2.5.0\"\] -- update-in :plugins conj \[cider/cider-nrepl\ \"0.25.0\"\] -- repl
```

Truncated/annotated EDN output:

```clojure
{:buggy? true,
 :java-home "/home/lvh/.local/graalvm-ce-java11-20.1.0",
 :java-vendor "GraalVM CE 20.1.0",
 :java-version "11.0.7",
 :alleged-classpath-length 1,
 :alleged-classpath
 [#object[java.io.File 0x611ed3bb "/home/lvh/.local/graalvm-ce-java11-20.1.0/lib/src.zip"]],
 :whence-alleged-classpath
 [[#object[clojure.lang.DynamicClassLoader 0x4ea4f179 "clojure.lang.DynamicClassLoader@4ea4f179"]
   ()]
  [#object[clojure.lang.DynamicClassLoader 0x2e76d0d3 "clojure.lang.DynamicClassLoader@2e76d0d3"]
   ()]
  [#object[clojure.lang.DynamicClassLoader 0x3123ba75 "clojure.lang.DynamicClassLoader@3123ba75"]
   ()]
  [#object[clojure.lang.DynamicClassLoader 0x2082e0e4 "clojure.lang.DynamicClassLoader@2082e0e4"]
   ()]
  [#object[clojure.lang.DynamicClassLoader 0x21d8bcbe "clojure.lang.DynamicClassLoader@21d8bcbe"]
   ()]
  [#object[clojure.lang.DynamicClassLoader 0x5ed731d0 "clojure.lang.DynamicClassLoader@5ed731d0"]
   (#object[java.io.File 0x77d8e452 "/home/lvh/.local/graalvm-ce-java11-20.1.0/lib/src.zip"])] ;; <= note this ClassLoader introduces a real path!
  [#object[jdk.internal.loader.ClassLoaders$AppClassLoader 0x55f96302 "jdk.internal.loader.ClassLoaders$AppClassLoader@55f96302"]
   ()]
  [#object[jdk.internal.loader.ClassLoaders$PlatformClassLoader 0x75def60c "jdk.internal.loader.ClassLoaders$PlatformClassLoader@75def60c"]
   ()]],
 :property-classpath-length 13,
 :property-classpath
 [#object[java.io.File 0x49374ba2 "/home/lvh/src/Latacora/graal-empty-classpath-reproducer/test"]
  #object[java.io.File 0x6f5498e8 "/home/lvh/src/Latacora/graal-empty-classpath-reproducer/src"]
  #object[java.io.File 0x37e0c941 "/home/lvh/src/Latacora/graal-empty-classpath-reproducer/dev-resources"]
  #object[java.io.File 0x43d98657 "/home/lvh/src/Latacora/graal-empty-classpath-reproducer/resources"]
  #object[java.io.File 0x1b84c8d8 "/home/lvh/src/Latacora/graal-empty-classpath-reproducer/target/default/classes"]
  #object[java.io.File 0x50dc9098 "/home/lvh/.m2/repository/cider/cider-nrepl/0.25.0/cider-nrepl-0.25.0.jar"]
  #object[java.io.File 0x6147f103 "/home/lvh/.m2/repository/refactor-nrepl/refactor-nrepl/2.5.0/refactor-nrepl-2.5.0.jar"]
  #object[java.io.File 0x6e232842 "/home/lvh/.m2/repository/nrepl/nrepl/0.6.0/nrepl-0.6.0.jar"]
  #object[java.io.File 0x535cff56 "/home/lvh/.m2/repository/org/clojure/clojure/1.10.0/clojure-1.10.0.jar"]
  #object[java.io.File 0x67b0fdf5 "/home/lvh/.m2/repository/org/clojure/spec.alpha/0.2.176/spec.alpha-0.2.176.jar"]
  #object[java.io.File 0x3921c7e9 "/home/lvh/.m2/repository/org/clojure/core.specs.alpha/0.2.44/core.specs.alpha-0.2.44.jar"]
  #object[java.io.File 0x4eb789b7 "/home/lvh/.m2/repository/org/clojure/java.classpath/1.0.0/java.classpath-1.0.0.jar"]
  #object[java.io.File 0x7aa56978 "/home/lvh/.m2/repository/clojure-complete/clojure-complete/0.2.5/clojure-complete-0.2.5.jar"]]}

```

This command is actually minimal! The bug only appears when using the `repl`
command (not with `run`) and introducing the `refactor-nrepl` middleware. We
expect that the bug is not actually in `refactor-nrepl` and that any middleware
would trigger the issue. (You can not introduce `refactor-nrepl` without also
introducing `cider-nrepl` and `nrepl`.) We believe that the underlying issue is
due to pomegrante/magic classpath injection.

[cpalgo]: https://github.com/clojure/java.classpath/blob/master/src/main/clojure/clojure/java/classpath.clj#L71

## License

Copyright © 2020 Latacora, LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
