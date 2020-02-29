#!/usr/bin/env lumo
(ns clap.cli
  (:require
    [child_process :as cp]
    [fs]
    [path]
    [yargs]))

(.splice (.-argv js/process) 2 1)

(def gulp-cli (js/require  "gulp-cli"))

(defn argv->array
  [argv]
  (.from js/Array argv))

(defn slice
  ([array start]
   (.slice array start))
  ([array start end]
   (.slice array start end)))

(defn args->map
  [args-array]
  (.parse yargs args-array))

(defn parse-argv
  [argv]
  (-> argv
      (argv->array)
      (slice 2)
      (args->map)
      (js->clj)
      (dissoc "$0")))

(defn file-exists?
  [filepath]
  (try
   (.accessSync fs filepath (.. fs -constants -R_OK))
   true
   (catch js/Error e
     nil)))

(defn recursively-find-task-file
  [init-path]
  (loop [current-path init-path]
    (let [file-path (.join path current-path "gulpfile.cljs")]
      (cond
       (file-exists? file-path) current-path
       (= current-path "/")     nil
       :else (recur (.resolve path current-path ".."))))))

(defn lumo-exec
  [dir filename]
  (.spawn cp "lumo" #js [filename]
          #js {:cwd dir
               :stdio "inherit"}))

(println (parse-argv (.-argv js/process)))
(println (.cwd js/process))
(.log js/console (.getSourcePaths js/$$LUMO_GLOBALS))

(let [gulpdir (recursively-find-task-file (.cwd js/process))
      gulpfile (.join path gulpdir "gulpfile.cljs")]
  ;; TODO: Add gulpdir to source paths
  (.execute_path (.-repl js/lumo) gulpfile))

(gulp-cli)

