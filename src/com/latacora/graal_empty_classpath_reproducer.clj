(ns com.latacora.graal-empty-classpath-reproducer
  (:require
   [clojure.java.classpath :as cp]
   [clojure.pprint :as pp]
   [clojure.string :as str])
  (:gen-class))

(defn -main
  "Print classpaths, computed two ways, plus their length"
  [& args]
  (let [alleged-classpath (cp/classpath)
        property-classpath (str/split
                            (System/getProperty "java.class.path")
                            (re-pattern (System/getProperty "path.separator")))]
    (pp/pprint
     {:alleged-classpath-length (count alleged-classpath)
      :alleged-classpath (vec alleged-classpath)

      :property-classpath-length (count property-classpath)
      :property-classpath (vec property-classpath)})))
