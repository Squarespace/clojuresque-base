(ns clojuresque.codox.reader
  "Read raw documentation information from Clojure source directory."
  (:use [clojuresque.codox.utils :only (unindent)])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojuresque.clojure.tools.namespace :as ns])
  (:import java.util.jar.JarFile))

(defn- correct-indent [text]
  (if text
    (let [lines (str/split-lines text)]
      (->> (rest lines)
           (str/join "\n")
           (unindent)
           (str (first lines) "\n")))))

(defn- sorted-public-vars [namespace]
  (->> (ns-publics namespace)
       (vals)
       (filter (comp :doc meta))
       (sort-by (comp :name meta))))

(defn- read-publics [namespace]
  (for [var (sorted-public-vars namespace)]
    (-> (meta var)
        (select-keys [:name :file :line :arglists :doc :macro :added])
        (update-in [:doc] correct-indent))))

(defn- read-ns [namespace]
  (try
    (require namespace :reload)
    (-> (find-ns namespace)
        (meta)
        (assoc :name namespace)
        (assoc :publics (read-publics namespace))
        (update-in [:doc] correct-indent)
        (list))
    (catch Exception e
      (println "Could not generate documentation for" namespace))))

(defn- jar-file? [file]
  (and (.isFile file)
       (-> file .getName (.endsWith ".jar"))))

(defn- find-namespaces [file]
  (if (jar-file? file)
    (ns/find-namespaces-in-jarfile (JarFile. file))
    (ns/find-namespaces-in-dir file)))

(defn read-namespaces
  "Read namespaces from a source directory (defaults to \"src\"), and
  return a list of maps suitable for documentation purposes.

  The keys in the maps are:
    :name   - the name of the namespace
    :doc    - the doc-string on the namespace
    :author - the author of the namespace
    :publics
      :name     - the name of a public function, macro, or value
      :file     - the file the var was declared in
      :line     - the line at which the var was declared
      :arglists - the arguments the function or macro takes
      :doc      - the doc-string of the var
      :macro    - true if the var is a macro
      :added    - the library version the var was added in"
  ([]
     (read-namespaces "src"))
  ([path]
     (->> (io/file path)
          (find-namespaces)
          (mapcat read-ns)))
  ([path & paths]
     (mapcat read-namespaces (cons path paths))))
