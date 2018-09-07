(ns cmr.plugin.jar.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [cmr.plugin.jar.util :refer [when-let*]]
   [taoensso.timbre :as log])
 (:import
  (java.util.jar JarFile)
  (java.util.jar Attributes$Name)))

(defn named-jars
  "Generate a collection of `JarFile` maps, each with a `:file` and `:object`
  key for easy readability and use."
  [jarfiles]
  (mapv #(hash-map :file (.getName %) :object %) jarfiles))

(defn no-manifest-reducer
  "This reducer will generate a collection of JAR files that have no MANIFEST file.
  Primarily useful for debugging/curiosity."
  [acc jarfile]
  (conj acc
        (when-not (.getManifest jarfile)
          jarfile)))

(defn create-has-manifest-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file."
  []
  (fn [acc jarfile]
    (conj acc
          (when (.getManifest jarfile)
            jarfile))))

(defn create-has-plugin-name-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that also have a key in the MANIFEST file which
  exactly matches the given pluging name."
  [plugin-name]
  (fn [acc jarfile]
    (conj acc
          (when-let* [m (.getManifest jarfile)
                      attrs (.getMainAttributes m)]
            (when (.containsKey attrs (new Attributes$Name plugin-name))
              jarfile)))))

(defn create-regex-plugin-type-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that:
  1) have a key in the MANIFEST file which exactly matches the given plugin
     name, and
  2) have a value for the plugin key that matches the configured plugin type."
  [plugin-name plugin-type]
  (fn [acc jarfile]
    (conj acc
          (when-let* [m (.getManifest jarfile)
                      attrs (.getMainAttributes m)
                      p-type (.getValue attrs plugin-name)]
            (when (re-matches (re-pattern plugin-type) p-type)
              jarfile)))))

(defn config-data
  "Extract the EDN configuration data stored in a jarfile at the given location
  in the JAR."
  [^JarFile jarfile in-jar-filepath]
  (doall
    (->> in-jar-filepath
         (.getEntry jarfile)
         (.getInputStream jarfile)
         slurp
         edn/read-string)))

(defn jarfiles
  "Given a plugin name (MANIFEST file entry), plugin type (the MANIFEST file
  entry's value), and a reducer-factory function, return all the JAR files
  that are accumulated by the redcuer."
  [^String plugin-name ^String plugin-type reducer]
  (doall
    (->> (classpath/classpath-jarfiles)
         (reduce (reducer plugin-name plugin-type)
                 [])
         (remove nil?))))