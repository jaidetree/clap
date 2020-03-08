#!/usr/bin/env lumo
(ns clap.cli
  (:require
    [child_process :as cp]
    [fs]
    [path]
    [goog.object :as goog-obj]
    [gulp]
    [gulplog :as log]
    [mute-stdout]
    ["gulp-cli/lib/shared/ansi" :as ansi]
    ["gulp-cli/lib/shared/cli-options" :as cli-options]
    ["gulp-cli/lib/shared/exit" :as exit]
    ["gulp-cli/lib/shared/get-blacklist" :as get-blacklist]
    ["gulp-cli/lib/shared/log/to-console" :as to-console]
    ["gulp-cli/lib/shared/tildify" :as tildify]
    ["gulp-cli/lib/shared/verify-dependencies" :as verify-deps]
    ["gulp-cli/lib/versioned/^4.0.0/log/tasks-simple" :as log-tasks-simple]
    ["gulp-cli/package.json" :as gulp-pkg]
    ;; Logging
    ["gulp-cli/lib/versioned/^4.0.0/log/events" :as log-events]
    ["gulp-cli/lib/versioned/^4.0.0/log/sync-task" :as log-sync-task]
    ["gulp-cli/lib/versioned/^4.0.0/log/tasks-simple" :as log-tasks-simple]
    ["gulp-cli/lib/versioned/^4.0.0/log/get-task" :as get-task]
    ["gulp-cli/lib/shared/log/copy-tree" :as copy-tree]
    ["gulp-cli/lib/shared/log/tasks" :as log-tasks]
    ["gulp-cli/lib/shared/log/verify" :as log-verify]
    ["gulp-cli/lib/shared/log/blacklist-error" :as log-blacklist-error]))

;; var logEvents = require('./log/events');
;; var logSyncTask = require('./log/sync-task');

;; Set env var for ORIGINAL path before anything touches it
;; https://github.com/gulpjs/gulp-cli/blob/2d8a320c834e615478d418256cc0d7b010bc8c84/index.js#L35
;; process.env.INIT_CWD = process.cwd();
(def cwd (.cwd js/process))
(set! (.. js/process -env -INIT_CWD) cwd)

;; Remove the CLI args lumo adds
(.splice (.-argv js/process) 2 1)

;; While you may wish to put these in the :require list we need to manipulate
;; process.argv first to remove the lumo CLI args

(def yargs (js/require "yargs"))
(def gulp-cli (js/require "gulp-cli"))

(defn usage
  "
  CLI usage help text
  "
  []
  (str "$0\n" (.bold ansi "Usage:") " gulp " (.blue ansi "[options]") " tasks"))

(defn argv->parser
  "
  Parse process.argv against gulp's cli options
  Takes a JS yargs obj of cli options
  Returns a parsed JS obj of supplied and defaults args
  "
  [cli-options]
  (as-> (usage) $
        (.usage yargs $)
        (.options $ cli-options)))

(defn file-exists?
  "
  Determine if a file is readable on disk
  Takes a filepath to test
  Returns true if file is readable false if not
  "
  [filepath]
  (try
   (.accessSync fs filepath (.. fs -constants -R_OK))
   true
   (catch js/Error e
     false)))

(defn join-path
  "
  Combine path strs according to host os
  Takes any str paths
  Returns str of joined paths
  "
  [& args]
  (apply (.-join path) args))

(defn recursively-find-task-file
  "
  Climb directory tree upwards from current directory looking for nearest
  parent with a gulpfile.cljs.
  Takes the initial path to search in.
  Returns the directory str the gulpfile.cljs was found in or nil
  "
  [init-path]
  (loop [current-path init-path]
    (let [file-path (join-path current-path "gulpfile.cljs")]
      (cond
       (file-exists? file-path) current-path
       (= current-path "/")     nil
       :else (recur (.resolve path current-path ".."))))))

(defn read-pkg
  "
  Load the package.json relative to where the target gulpfile.cljs was found
  Takes a directory string
  Returns the package js obj or an empty js obj
  "
  [dir]
  (when dir
    (let [pkg-path (join-path dir "package.json")]
      (if (and dir (file-exists? pkg-path))
        (js/require pkg-path)
        #js {}))))

(def parser (argv->parser cli-options))
(def opts (.-argv parser))
(def gulpdir (recursively-find-task-file cwd))
(def pkg (read-pkg gulpdir))
(def gulpfile (join-path gulpdir "gulpfile.cljs"))

;; CLI Commands

(defn help
  "
  Print CLI options to console
  "
  [opts]
  (.showHelp parser (.-log js/console))
  (exit 0))

(defn version
  "
  Display lib version
  "
  [opts]
  (println "CLI version:" (.-version gulp-pkg))
  (println "Local version:" (or (.-version pkg) "Unknown"))
  (exit 0))

;; Set UNDERTAKER_SETTLE ENV flag if continuing a series
(when (.-continue opts)
  (set! (.. js/process -env -UNDERTAKER_SETTLE) "true"))

(defn verify
  "
  Verify library and ensure no blacklisted plugins are used
  "
  [opts]
  (let [pkg-file (if (= (.-verify opts) true)
                   "package.json"
                   (.-verify opts))
        pkg-path (if (= (.resolve path pkg-file) (.normalize path pkg-file))
                   pkg-file
                   (join-path gulpdir pkg-file))]
    (.info log (str "Verifying plugins in" pkg-path))
    (get-blacklist
     (fn [err blacklist]
       (if err
         (log-blacklist-error err)
         (do
           (log-verify
            (verify-deps (js/require pkg-path) blacklist))))))))

(defn format-file
  "
  Tildifies the file path and colors it magenta
  Takes a path string
  "
  [path-str]
  (.magenta ansi (tildify path-str)))

(defn no-gulpfile
  "
  Tell the user no gulpfile was found
  "
  [opts]
  (.error log (str
               "Could not find gulpfile.cljs in"
               (format-file cwd)
               "or any parent directories"))
  (exit 1))

;; General CLI Command Dispatcher

(to-console log opts)

(cond
  (.-help opts)    (help opts)
  (.-version opts) (version opts)
  (.-verify opts)  (verify opts)
  (nil? gulpdir)    (no-gulpfile opts))


(when (not= cwd gulpdir)
  (.chdir js/process gulpdir)
  (.info log "Working directory changed to"
         (format-file gulpdir)))

;; Gulp-Specific Command Dispatcher

;; Load and evaluate gulpfile.cljs to create tasks
(when gulpfile
  (.execute_path (.-repl js/lumo) gulpfile))

(log-events gulp)
(log-sync-task gulp)

(defn simple-tasks
  "
  Output a list of task names
  Takes JS obj of parsed yargs args
  "
  [opts]
  (let [tree (.tree gulp)]
    (log-tasks-simple (.-nodes tree))))

(defn tasks-tree
  "
  Get a tree of gulp tasks and assign a label with source gulpfile
  Takes optional arg keywords:
  - :color boolean - If the gulpfile should be colored
  Returns a gulp task tree instance
  "
  [& {:keys [color]}]
  (let [tree (.tree gulp #js {:deep true})
        label (if color
                (format-file gulpfile)
                (tildify gulpfile))]

    (set! (.-label tree)
          (str "Tasks for " label))
    tree))

(defn show-tasks
  "
  Display the task tree to console
  Takes JS obj of parsed yargs args
  "
  [opts]
  (let [tree (tasks-tree :color true)]
    (log-tasks tree opts (get-task gulp))))

(defn json-tasks
  "
  Output the gulp task tree as JSON to consoleor output to another file
  Takes JS obj of parsed yargs args
  "
  [opts]
  (let [output-file (.-tasksJson opts)
        tree (tasks-tree)
        json (.stringify js/JSON (copy-tree tree opts))]
    (if (and (boolean? output-file) output-file)
      (.log js/console json)
      (.writeFileSync fs output-file json "utf-8"))))

(defn get-tasks
  "
  Parse tasks from cli args will run default task if none given
  Takes JS obj of parsed yargs args
  Returns JS array of task names
  "
  [opts]
  (let [tasks (.-_ opts)]
    (if (zero? (count tasks))
      #js ["default"]
      tasks)))

(defn task-runner
  "
  Create a thunk to run tasks in series or parallel
  Takes JS obj of parsed yargs args and a JS array of tasks
  Returns a function that takes a callback to call after tasks complete
  "
  [opts tasks]
  (if (.-series opts)
    (.series gulp tasks)
    (.parallel gulp tasks)))

(defn on-complete
  "
  Callback when tasks finish exits with error status if err was thrown
  Takes an error instance or null
  "
  [err]
  (when err
    (exit 1)))

(defn run-tasks
  "
  Runs the specified gulp tasks from CLI args or default
  Takes JS obj of parsed yargs args from cli options
  "
  [opts]
  (try
   (let [tasks (get-tasks opts)
         run (task-runner opts tasks)]
     (.unmute mute-stdout)
     (.info log "Using gulpfile" (format-file gulpfile))
     (run on-complete))
   (catch js/Error e
     (.error log (.red ansi (.-message e)))
     (.error log "To list available tasks, try running: gulp --tasks")
     (exit 1))))

(cond
  (.-tasksSimple opts) (simple-tasks opts)
  (.-tasks opts)       (show-tasks opts)
  (.-tasksJson opts)   (json-tasks opts)
  :else                (run-tasks opts))

