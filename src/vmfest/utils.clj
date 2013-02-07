(ns vmfest.utils
  (:require
   [clojure.java.io :as io])
  (:import
   [org.apache.commons.compress.archivers ArchiveStreamFactory]))

(defn delete-file
  "Delete file f. Raise an exception if it fails unless silently is true."
  [f & [silently]]
  (or (.delete (io/file f))
      silently
      (throw (java.io.IOException. (str "Couldn't delete " f)))))

(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
   Raise an ExceptionInInitializerError/ if any deletion fails unless silently
   is true."
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (delete-file f silently)))

(defn untar [file dest]
  (let [input (.. (ArchiveStreamFactory.)
                  (createArchiveInputStream (io/input-stream file)))]
    (loop [entry (.getNextEntry input)]
      (when entry
        (let [target (io/file dest (.getName entry))]
          (if (.isDirectory entry)
            (.mkdirs target)
            (io/copy input target)))
        (recur (.getNextEntry input))))))
