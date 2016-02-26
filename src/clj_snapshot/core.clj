(ns clj-snapshot.core
  (:require
    [clojure.java.io :as io]
    [taoensso.nippy :as nippy])
  (:import
    [java.io DataInputStream DataOutputStream]))

(defprotocol SettableState
  (set-state [this state]))

(defrecord MultiReference [refs]
  SettableState
  (set-state
    [_ states]
    (dosync
      (dorun (map set-state refs states))))

  clojure.lang.IDeref
  (deref
    [_]
    (mapv deref refs)))

(prefer-method print-method java.util.Map clojure.lang.IDeref)

(extend-protocol SettableState
  clojure.lang.Atom
  (set-state
    [a state]
    (reset! a state))

  clojure.lang.Ref
  (set-state
    [r state]
    (dosync (ref-set r state))))


(defn- read-file
  [file]
  (with-open [r (io/input-stream file)]
    (nippy/thaw-from-in! (DataInputStream. r))))

(defn- write-file
  [file freezable]
  (with-open [o (io/output-stream file)]
    (nippy/freeze-to-out! (DataOutputStream. o) freezable)))

(defn snapshotter!
  "Creates a new Closeable snapshotter using initial-state or getting its own
  state from the reader-or-file if available"
  ([{:keys [pool file period] :or [period 1000]} state]
    (when (-> file io/file .canRead)
      (set-state state (read-file file)))
    ;; TODO claypoole
    (let [running (atom true)]
      (future
        (while @running
          (Thread/sleep period)
          (write-file file @state)))
      (reify java.io.Closeable
        (close [_] (reset! running false)))))
  ([opts state & states]
    (snapshotter! opts (->MultiReference (cons state states)))))
;
; #_(snapshotter! {} (atom {}))
;
; #_(defn start-app
;   (let [state (atom {})
;         snapshotter (snapshotter! {:file "snapshot.nip"})]))
