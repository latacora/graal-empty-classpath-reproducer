(ns com.latacora.graal-empty-classpath-reproducer
  (:require
   [clojure.java.classpath :as cp]
   [clojure.pprint :as pp])
  (:gen-class))

(defn -main
  "Print classpaths, computed two ways, plus their length"
  [& args]
  (let [alleged-classpath (cp/classpath)
        system-classpath (cp/system-classpath)]
    (pp/pprint
     {;; while these don't always have to be equal, even for a non-buggy impl,
      ;; this is the simplest way to see if the bug is occurring in our test.
      :buggy? (not= alleged-classpath system-classpath)

      :java-home (System/getProperty "java.home")
      :java-vendor (System/getProperty "java.vendor")
      :java-version (System/getProperty "java.version")

      :alleged-classpath-length (count alleged-classpath)
      :alleged-classpath (if (-> alleged-classpath count (= 1))
                           (vec alleged-classpath)
                           :too-long)

      :whence-alleged-classpath (->> (clojure.lang.RT/baseLoader)
                                     (iterate #(.getParent ^ClassLoader %))
                                     (take-while identity)
                                     (mapv (juxt identity cp/loader-classpath)))

      :property-classpath-length (count system-classpath)
      :property-classpath (vec system-classpath)})))
