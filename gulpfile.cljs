(ns gulpfile.tasks
  (:require
    [gulp]))

(println "I am imported!")

(.task gulp "test"
       (fn test-task
         [done]
         (println "I am a task")
         (done)))
