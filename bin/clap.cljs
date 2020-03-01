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
    ["gulp-cli/package.json" :as gulp-pkg]
    ;; Logging
    ["gulp-cli/lib/versioned/^4.0.0/log/events" :as log-events]
    ["gulp-cli/lib/versioned/^4.0.0/log/sync-task" :as log-sync-task]
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
  []
  (str "$0\n" (.bold ansi "Usage:") " gulp " (.blue ansi "[options]") " tasks"))

(defn argv->parser
  [cli-options]
  (as-> (usage) $
        (.usage yargs $)
        (.options $ cli-options)))

(defn file-exists?
  [filepath]
  (try
   (.accessSync fs filepath (.. fs -constants -R_OK))
   true
   (catch js/Error e
     nil)))

(defn join-path
  ([p1 p2]
   (apply join-path [nil p1 p2]))
  ([default & args]
   (if (and (nth args 0) (nth args 1))
     (apply (.-join path) args)
     default)))

(defn recursively-find-task-file
  [init-path]
  (loop [current-path init-path]
    (let [file-path (join-path current-path "gulpfile.cljs")]
      (cond
       (file-exists? file-path) current-path
       (= current-path "/")     nil
       :else (recur (.resolve path current-path ".."))))))

(defn read-pkg
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
  [opts]
  (.showHelp parser (.-log js/console))
  (exit 0))

;; console.log('CLI version:', cliVersion);
;; console.log('Local version:', env.modulePackage.version || 'Unknown');
;; exit(0);

(defn version
  [opts]
  (println "CLI version:" (.-version gulp-pkg))
  (println "Local version:" (or (.-version pkg) "Unknown"))
  (exit 0))

(when (.-continue opts)
  (set! (.. js/process -env -UNDERTAKE_SETTLE) "true"))

(defn verify
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

(defn no-gulpfile
  [opts]
  (.error log (str
               "Could not find gulpfile.cljs in"
               (.magenta ansi (tildify cwd))
               "or any parent directories"))
  (exit 1))

;; CLI Command Dispatcher

(to-console log opts)

(cond
  (.-help opts)    (help opts)
  (.-version opts) (version opts)
  (.-verify opts)  (verify opts)
  (nil? gulpdir)    (no-gulpfile opts))


(when (not= cwd gulpdir)
  (.chdir js/process gulpdir)
  (.info log "Working directory changed to"
         (.magenta ansi (tildify gulpdir))))


;; Load and evaluate gulpfile.cljs to create tasks
(when gulpfile
  (.execute_path (.-repl js/lumo) gulpfile))

; (gulp-cli)
; (execute-tasks opts env config)
; (println opts)
;; (gulp-cli)

(log-events gulp)
(log-sync-task gulp)

(.nextTick
 js/process
 (fn []
   (try
     (let [tasks (if (pos? (count (.-_ opts)))
                   (.-_ opts)
                   #js ["default"])
           handler (fn [err]
                     (when err
                       (exit 1)))]
       (.unmute mute-stdout)
       (.info log "Using gulpfile" (.magenta ansi (tildify gulpfile)))
       (if (.-series opts)
         (.series gulp tasks handler)
         (.parallel gulp #js ["test"] handler)))
     (catch js/Error e
       (println "Something went wrong")
       (.error log (.red ansi (.-message e)))
       (.error log "To list available tasks, try running: gulp --tasks")
       (exit 1)))))
