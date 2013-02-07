(ns vmfest.virtualbox.image
  (:use [clojure.java.io]
        [vmfest.virtualbox.session :only (with-vbox)]
        [clojure.pprint :only (pprint)]
        [vmfest.utils :only [untar]]
        [vmfest.virtualbox.virtualbox :only (find-medium)]
        [vmfest.virtualbox.system-properties :only [supported-medium-formats
                                                    default-hard-disk-format]]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [vmfest.virtualbox.enums :as enums])
  (:import [org.virtualbox_4_2 DeviceType AccessMode MediumVariant
            MediumType IMedium MediumState IProgress IVirtualBox]
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

(defn register [vbox ^String image]
  (with-vbox vbox [_ vb]
    (.openMedium
     ^IVirtualBox vb image DeviceType/HardDisk AccessMode/ReadOnly false)))

(defn make-immutable [^IMedium medium]
  (.setType medium MediumType/MultiAttach))

(defn directory-and-file-name-from-url [^String url]
  (let [last-forward-slash-index (+ 1 (.lastIndexOf url "/"))]
    [(.substring url 0 last-forward-slash-index)
     (.substring url last-forward-slash-index )]))

(defn file-name-without-extensions [file-name]
  (-> file-name
      (string/replace #"\.gz$" "")
      (string/replace #"\.[^.]+$" "")))

(defn register-model [^String orig ^String dest vbox]
  (with-vbox vbox [_ ^IVirtualBox vb]
    (let [^IVirtualBox vb vb
          orig-medium
          (.openMedium vb orig DeviceType/HardDisk AccessMode/ReadOnly false)
          dest-medium (.createHardDisk vb "vdi" dest)
          progress (.cloneTo orig-medium dest-medium (long 0) nil)]
      ;; wait indefinitely for the cloning
      (.waitForCompletion progress (Integer. -1))
      (make-immutable dest-medium)
      ;; We need to close the origial medium, otherwise it would
      ;; remain registered
      (.close orig-medium))))

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
   & {:keys [model-name temp-dir meta-file-name
             meta-url meta model-file-name model-path]
      :as options}]
  (let [[directory ^String image-file-name] (directory-and-file-name-from-url
                                             image-url)
        image-name (file-name-without-extensions image-file-name)
        gzipped? (.endsWith image-file-name ".gz")
        vagrant-box? (.endsWith image-file-name ".box")
        model-name (or model-name image-name)
        model-unique-name (str "vmfest-" model-name)
        meta-url (or meta-url
                     (str directory (or meta-file-name
                                        (str image-name ".meta"))))
        temp-dir (make-temp-dir model-unique-name)
        model-path (or model-path (str (System/getProperty "user.home")
                                     File/separator
                                     ".vmfest/models"))]
    {:image-url image-url
     :image-file-name image-file-name
     :gzipped? gzipped?
     :gzipped-image-file (str temp-dir File/separator image-file-name)
     :vagrant-box? vagrant-box?
     :image-file (if vagrant-box?
                   (str temp-dir File/separator "box-disk1.vmdk")
                   (str temp-dir File/separator image-name ".vdi"))
     :model-name model-name
     :model-path model-path
     :model-file (if vagrant-box?
                   (str model-path File/separator model-unique-name ".vmdk")
                   (str model-path File/separator model-unique-name ".vdi"))
     :model-meta (str model-path File/separator model-unique-name ".meta")
     :temp-dir temp-dir
     :meta meta
     :meta-url (when-not meta meta-url)
     :image-name image-name
     :vbox vbox}))

(def ^:dynamic *dry-run* false)

(defn threaded-download
  [{:keys [model-name image-url gzipped? gzipped-image-file image-file
           vagrant-box?]
    :as options}]
  (let [dest (if (or gzipped? vagrant-box?) gzipped-image-file image-file)]
    (log/infof "%s: Downloading %s into %s" model-name image-url dest )
    (when-not *dry-run*
      (download image-url dest)))
  options)

(defn threaded-gunzip
  [{:keys [model-name gzipped? gzipped-image-file image-file] :as options}]
  (if gzipped?
    (do
      (log/infof "%s: Gunzipping %s into %s"
                 model-name gzipped-image-file image-file)
      (when-not *dry-run*
        (gunzip gzipped-image-file image-file)))
    (log/infof "%s: File %s is already uncompressed" model-name image-file))
  options)

(defn threaded-unbox
  [{:keys [model-name vagrant-box? temp-dir gzipped-image-file] :as options}]
  (if vagrant-box?
    (do
      (log/infof "%s: Unpacking %s into %s"
                 model-name gzipped-image-file temp-dir)
      (when-not *dry-run*
        (untar gzipped-image-file temp-dir)))
    (log/infof "%s: Fileis not a vagrant box" model-name))
  options)

(defn threaded-get-metadata
  [{:keys [model-name image-file meta meta-url vagrant-box?] :as options}]
  (if vagrant-box?
    (do
      (log/infof "%s: Creating metadata for vagrant box" model-name)
      (update-in options [:meta]
                 #(merge
                   {:username "vagrant"
                    :password "vagrant"
                    :sudo-password "vagrant"
                    :no-sudo false
                    :network-type :nat}
                   %)))
    (if meta
      (do
        (log/infof "%s: Metadata provided explicitly" model-name)
        options)
      (do
        (log/infof "%s: Loading metadata from %s" model-name meta-url)
        (assoc options :meta (load-string (slurp meta-url)))))))

(defn threaded-register-model
  [{:keys [image-file model-file vbox model-name] :as options}]
  (log/infof "%s: Registering image %s as %s in %s"
             model-name image-file model-file vbox)
  (when-not *dry-run*
    (register-model image-file model-file vbox))
  options)

(defn threaded-create-meta
  [{:keys [model-name meta model-meta model-file] :as options}]
  (let [meta (assoc meta :uuid model-file)
        meta {(keyword model-name) meta}]
    (log/infof "%s: Creating meta file %s with %s" model-name model-meta meta)
    (when-not *dry-run*
      (spit model-meta meta))
    (assoc options :meta meta)))

(defn threaded-cleanup-temp-files
  [{:keys [gzipped-image-file image-file] :as options}]
  (delete-file gzipped-image-file true)
  (delete-file image-file true)
  options)

(defn mutable? [medium]
   (let [type (enums/medium-type-type-to-key (.getType medium))]
     (or (= type :immutable) (= type :multi-attach))))

(defn valid-model? [vbox id-or-location]
  ;; is the image registered?
  (let [medium (find-medium vbox id-or-location)]
    (if-not medium
      (throw+ {:type :model-not-registered
               :message
               (str "This model's image is not registered in VirtualBox: "
                    id-or-location)}))
    ;; is the image immutable?
    (if-not (mutable? medium)
      (throw+ {:type :model-not-immutable
               :message
               (str "This model's image is not immutable nor multi-attach: "
                    id-or-location)}))))

(defn setup-model
  "Download a disk image from `image-url` and register it with `vbox`. Returns a
  map with at least `:model-name` and `:meta` keys."
  [image-url vbox & {:as options}]
  (let [job (apply prepare-job image-url vbox (reduce into [] options))]
    (log/info (str "About to execute job \n" (with-out-str (pprint job))))
    (if (.exists (File. ^String (:model-file job)))
      (log/errorf
       "The model %s already exists. Manually specifiy another file name with :model-name"
       (:model-file job))
      (-> job
          threaded-download
          threaded-gunzip
          threaded-unbox
          threaded-get-metadata
          threaded-register-model
          threaded-create-meta
          threaded-cleanup-temp-files))))


(comment
 (use 'vmfest.manager)
 (use 'vmfest.virtualbox.image)
 (def my-server (server "http://localhost:18083"))
 (setup-model "https://s3.amazonaws.com/vmfest-images/ubuntu-10-10-64bit-server.vdi.gz" my-server))


;;; new image creation

(defn- ^IProgress create-base-storage
  "Creates the actual base storage for this medium. If no variant
  flags are passed it will default to [].

  Returns an IProgress for this operation

  logical-size in MB
  variants is a sequence of MediumVariant keys
          (see enums/medium-variant-type-to-key-table) "
  [^IMedium medium logical-size variant-seq]
  (let [ ;; variants are flags. Get all the flags into a single long value
        variants (long (reduce bit-or 0
                               (map (comp #(.value ^MediumVariant %)
                                          enums/key-to-medium-variant)
                                    (or variant-seq []))))
        logical-size (long (* 1024 1024 logical-size))]
    (log/infof
     "create-base-storage: Creating medium with size %sMB and variants %s"
     logical-size variants)
    (.createBaseStorage medium (long logical-size) (long variants))))

(defn create-medium
  "Creates a hard disk in the path described by `location`.

  vbox: an IVirtualBox
  location: File path where the image will be created
  format: the format of the image. One in
       (system-properties/supported-medium-formats)
  size: Logical size, in MB
  variants: one or more of the variants in
      (enums/medium-variant-type-to-key-table)

  NOTE: not all formats and variants are supported for all hosts, nor
  all combinations of variatns are valid. Error reporting on this
  front is spotty at best."
  [^IVirtualBox vbox ^String location format-key size variants]
  (let [format-key
        ;; ensure that a format is specified. Not sure this is needed,
        ;; but at least it makes it explicit what format will be used
        ;; when none is specified
        (or format-key
            (let [format-key (keyword
                              (.toLowerCase
                               ^String (default-hard-disk-format vbox)))]
              (log/warnf
               "create-medium: No format specified for %s. Defaulting to %s"
               location format-key)
              format-key))]
    (if (some #(= format-key %) (supported-medium-formats vbox))
      ;; only get here when the required format is supported in this host
      (let [medium (.createHardDisk vbox (name format-key) location)
            progress (create-base-storage medium size variants)]
        (.waitForCompletion progress (Integer. -1)) ;; wait until it is done
        (when (= (.getState medium) MediumState/NotCreated)
          ;; oops. Image not created
          (throw+ {:type :image-not-created
                   :last-access-error (.getLastAccessError medium)
                   :message
                   (format
                    (str "Cannot create image. Check that the variants %s are"
                         " allowed in this host, or that the image doesn't"
                         " already exist in %s")
                    variants location)
                   :result-code (.getResultCode progress)}))
        medium)
      ;; the format is unsupported in this host
      (throw+ {:type :invalid-image-format
               :message
               (format (str "The format %s for image %s is not supported in this"
                            " host.")
                       format-key location)}))))
