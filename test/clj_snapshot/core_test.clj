(ns clj-snapshot.core-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [clj-snapshot.core :as core]))

(def test-dir "integration-tests")

(defmacro with-temp-file
  [[var-name file-name] & body]
  `(let [~var-name (io/file ~file-name)]
    (try
      ~@body
      (finally
        (.delete ~var-name)))))

(defn setup-directory
  [f]
  (with-temp-file [dir test-dir]
    (.mkdir dir)
    (f)))

(use-fixtures :once setup-directory)

(defmacro deftest-with-file
  [test-name [file-var-sym] & body]
  `(deftest ~test-name
    (with-temp-file
      [~file-var-sym (str test-dir "/" '~test-name)]
      ~@body)))

(deftest-with-file test-snapshotter-single-references
  [file]
  (let [a (atom 0)
        b (atom 5)
        r (ref "hello")
        smith (agent "smith")
        period 100]
    (with-open [snapshotter (core/snapshotter! {:file file :period period} a)]
      (swap! a inc)
      ;; Give it a chance to write
      (Thread/sleep (* 2 period)))
    (testing "read into another atom"
      (with-open [snapshotter (core/snapshotter! {:file file :period period} b)]
        (is (= @a @b) "b and a match after loading state from file.")
        (swap! b inc)
        (Thread/sleep (* 2 period))))
    (testing "read into a ref"
      (with-open [snapshotter (core/snapshotter! {:file file :period period} r)]
        (is (= @r @b) "r&b now match after loading r's state from file.")
        (is (not= @r @a))
        (dosync (commute r inc))
        (Thread/sleep (* 2 period))))
    (testing "read into agent"
      (with-open [snapshotter (core/snapshotter! {:file file :period period} smith)]
        (await smith)
        (is (= @r @smith))
        (send smith inc)
        (await smith)
        (Thread/sleep (* 2 period))))
    (testing "read back into a"
      (with-open [snapshotter (core/snapshotter! {:file file :period period} a)]
        (is (= @a @smith))))
    ;; Ensure snapshotter finished writing
    (Thread/sleep (* 2 period))))

(deftest-with-file test-snapshotter-multi-ref
  [file]
  (let [a (atom 0)
        b (atom 5)
        r (ref "hello")
        smith (agent "smith")
        period 100]
    (with-open [snapshotter (core/snapshotter! {:file file :period period} a b r smith)]
      (send smith str ", agent")
      (swap! a inc)
      (swap! b inc)
      (dosync (alter r str " friend"))
      (await smith)
      (Thread/sleep (* 2 period)))
    ;; Now be evil and read them back into different references
    (with-open [snapshotter (core/snapshotter! {:file file :period period} smith r a b)]
      (is (= @b "smith, agent"))
      (is (= @a "hello friend"))
      (is (= @r 6))
      (await smith)
      (is (= @smith 1)))
    ;; Ensure snapshotter finished writing
    (Thread/sleep (* 2 period))))
