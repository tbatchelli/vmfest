(ns vmfest.virtualbox.image
  (:use [clojure.java.io]
        [vmfest.virtualbox.session :only (with-vbox)]
        [clojure.pprint :only (pprint)])
  (:require [clojure.contrib.logging :as log])
  (:import [org.virtualbox_4_0 DeviceType AccessMode MediumVariant
            MediumType]
           [java.util.zip GZIPInputStream]
           [java.io File]))

(defn download [from to]
  (with-open [input (input-stream from)
              output (output-stream to)] 
    (copy input output :buffer-size (* 1024 1024))))

(defn gunzip [from to]
  (with-open [input (GZIPInputStream. (input-stream from))
              output (output-stream to)]
    (copy input output :buffer-size (* 1024 1024))))

(defn register [vbox image]
  (with-vbox vbox [_ vb]
    (.openMedium vb image DeviceType/HardDisk AccessMode/ReadOnly)))

(defn make-immutable [medium]
  (.setType medium MediumType/Immutable))

(defn directory-and-file-name-from-url [url]
  (let [last-forward-slash-index (+ 1 (.lastIndexOf url "/"))]
    [(.substring url 0 last-forward-slash-index)
     (.substring url last-forward-slash-index )]))

(defn file-name-without-extensions [file-name]
  (let [first-dot-index (.indexOf file-name ".")]
    (.substring file-name 0 first-dot-index)))

(defn register-model [orig dest vbox]
  (with-vbox vbox [_ vb]
    (let [orig-medium (.openMedium vb orig DeviceType/HardDisk AccessMode/ReadOnly)
          dest-medium (.createHardDisk vb "vdi" dest)
          progress (.cloneTo orig-medium dest-medium (long 0) nil)]
      (.waitForCompletion progress -1) ;; wait indefinitely for the cloning
      (make-immutable dest-medium)
      (.close orig-medium) ;; otherwise the origina medium would remain registered
      )))

(defn make-temp-dir [image-name]
  (let [tmp (System/getProperty "java.io.tmpdir")
        base-path (str tmp java.io.File/separator "vmfest-" image-name)
        base-file (File. base-path)]
    (if (and (not (.exists base-file))
             (.mkdir base-file))
      base-file
      (loop [n 1]
        (let [base-file (File. (str base-path "-" n))]
          (if  (and (not (.exists base-file))
                    (.mkdir base-file))
            base-file
            (recur (+ 1 n))))))))


(defn prepare-job
  [image-url vbox
   & {:keys [model-name temp-dir meta-file-name meta-url meta model-file-name models-dir]
      :as options}]
  (let [[directory image-file-name] (directory-and-file-name-from-url image-url)
        image-name (file-name-without-extensions image-file-name)
        gzipped? (.endsWith image-file-name ".gz")
        model-name (str "vmfest-" (or model-name image-name))
        meta-url (or meta-url (str directory (or meta-file-name (str image-name ".meta"))))
        temp-dir (make-temp-dir model-name)
        models-dir (or models-dir (str (System/getProperty "user.home")
                                     File/separator
                                     ".vmfest/models"))]
    {:image-url image-url
     :image-file-name image-file-name
     :gzipped? gzipped?
     :gzipped-image-file (str temp-dir File/separator image-file-name)
     :image-file (str temp-dir File/separator image-name ".vdi")
     :model-name model-name
     :models-dir models-dir
     :model-file (str models-dir File/separator model-name ".vdi")
     :model-meta (str models-dir File/separator model-name ".meta")
     :temp-dir temp-dir
     :meta meta
     :meta-url (when-not meta meta-url)
     :image-name image-name
     :vbox vbox}))

(def *dry-run* false)

(defn threaded-download
  [{:keys [model-name image-url gzipped? gzipped-image-file image-file] :as options}]
  (let [dest (if gzipped? gzipped-image-file image-file)]
    (log/info (format "%s: Downloading %s into %s" model-name image-url dest ))
    (when-not *dry-run*
      (download image-url dest)))
  options)

(defn threaded-gunzip
  [{:keys [model-name gzipped? gzipped-image-file image-file] :as options}]
  (if gzipped?
    (do
      (log/info (format "%s: Gunzipping %s into %s" model-name gzipped-image-file image-file))
      (when-not *dry-run*
        (gunzip gzipped-image-file image-file)))
    (log/info (format "%s: File %s is already uncompressed" model-name image-file)))
  options)

(defn threaded-get-metadata
  [{:keys [model-name image-file meta meta-url] :as options}]
  (if meta
    (do
      (log/info (format "%s: Metadata provided explicitly" model-name))
      options)
    (do
      (log/info (format "%s: Loading metadata from %s" model-name meta-url))
      (assoc options :meta (load-string (slurp meta-url))))))

(defn threaded-register-model
  [{:keys [image-file model-file vbox model-name] :as options}]
  (log/info (format "%s: Registering image %s as %s in %s"
                    model-name image-file model-file vbox))
  (when-not *dry-run*
    (register-model image-file model-file vbox))
  options)

(defn threaded-create-meta
  [{:keys [model-name meta model-meta model-file] :as options}]
  (let [meta (assoc meta :uuid model-file)
        meta {(keyword model-name) meta}]
    (log/info (format "%s: Creating meta file %s with %s" model-name model-meta meta))
    (when-not *dry-run*
      (spit model-meta meta))))

(defn setup-model [image-url vbox & {:as options}]
  (let [job (apply prepare-job image-url vbox (reduce into [] options))]
    (log/info (str "About to execute job \n" (with-out-str (pprint job))))
    (if (.exists (File. (:model-file job)))
      (log/error
       (format
        "Model %s already exists. Manually specifiy another file name with :model-name"
        (:model-file job) (:temp-dir job)
        false))
      (-> job
          threaded-download
          threaded-gunzip
          threaded-get-metadata
          threaded-register-model
          threaded-create-meta))))

(comment
 (use 'vmfest.manager)
 (use 'vmfest.virtualbox.image)
 (def my-server (server "http://localhost:18083"))
 (setup-model "https://s3.amazonaws.com/vmfest-images/ubuntu-10-10-64bit-server.vdi.gz" my-server)) 
