# clj-snapshot

Clojure library to write periodic snapshots of clojure references to disk.

Inspired by [snapshots in Redis](http://redis.io/topics/persistence).

## Usage

```clojure
(ns my.ns
  (:require
    [clj-snapshot.core :as snap]))

(defn -main
  [& args]
  (let [state (atom {})
        file "my-state.data"
        ;; Create a snapshotter to save `state` to the output file every second.
        ;; When this is run again, `state` will be read from the file.
        snapshotter (snap/snapshotter! {:file file :period 1000} state)]
    ...))
```

There's built-in support for `atom`, `ref`, and `agent`, or a combination. Example:

```clojure
(let [a (atom 0)
      r (ref 100)
      smith (agent "smith")
      file "multi-ref.data"
      ;; Reads/writes all of them to the same file.
      snapshotter (snap/snapshotter! {:file file :period 1000} a r smith)]
  ...)
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
