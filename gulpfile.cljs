(ns gulpfile.tasks
  (:require
    [gulp]))

(println "I am imported!")

(.task gulp "test"
       (fn [done]
         (println "I am a task")
         (done)))
