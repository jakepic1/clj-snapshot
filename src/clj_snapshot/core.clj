(ns clj-snapshot.core
  (:require
    [taoensso.nippy :as nippy]]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))


#_"
Keep it really fucking simple

1 snapshotter per datum (i.e. atom/agent)
eh but fuck that won't work with refs which need to be consistent... should be snapshotted at same time
start with atoms for now you twat

actually that will work, you just combine the refs together into a multireference

how to deal with custom records or classes? Nippy! extend-freeze and extend-thaw, easy
"


(defn snapshotter!
  "Creates a new Closeable snapshotter using initial-state or getting its own
  state from the reader-or-file if available"
  ([{:keys [pool file period]} state]
    (when (is-there? file)
      (set-state state (read-file )))
    (let [running (atom true)]
      (future
        (while @running
          (Thread/sleep period)
          (write file (nippy/freeze @state))))
      (reify java.io.Closeable
        (close [_] (reset! running false)))))
  ([opts & states]
    (snapshotter! opts (multi-ref states))))

(snapshotter! {} (atom {}))

(defn start-app
  (let [state (atom {})
        snapshotter (snapshotter! {:file "snapshot.nip"})]))
