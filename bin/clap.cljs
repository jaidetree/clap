#!/usr/bin/env lumo
(ns clap.cli
  (:require
    [fs]
    [path]
    [yargs]))

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
      (slice 3)
      (args->map)
      (js->clj)
      (dissoc "$0")))

(defn file-exists?
  [filepath]
  (try
   (.accessSync fs filepath (->> fs (.-constants) (.-R_OK)))
   true
   (catch js/Error e
     nil)))

(defn recursively-find-task-file
  [init-path]
  (loop [current-path init-path]
    (let [file-path (.join path current-path "gulpfile.cljs")]
      (cond
       (file-exists? file-path) file-path
       (= current-path "/")     nil
       :else (recur (.resolve path current-path ".."))))))

(println (parse-argv (.-argv js/process)))
(println (.cwd js/process))
(println (file-exists? (.join path (.cwd js/process) "gulpfile.cljs")))
(println (recursively-find-task-file (.cwd js/process)))
