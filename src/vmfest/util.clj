(ns vmfest.util
  (:import java.lang.Character)
  (:use [clojure.contrib.str-utils2 :only [split]]))

(defn case-type [c]
  (when-not (nil? c)
    (if (Character/isUpperCase c) :U :L)))

(defn camel-to-clojure-style* [words current-word remaining-chars last-char current-char]
  (let [last-char-case (case-type last-char)
        current-char-case (case-type current-char)]
    (let [rest-of-remaining-chars (rest remaining-chars)
          next-char (first remaining-chars)]
      ;; NOTICE: :U -> uper-case, :L -> lower case. 
      (case [last-char-case current-char-case] 
            ;; we ran out of characters
            ([:U nil] [:L nil] [nil nil])
            ;; we're done!, return all the words plus the current word
            (conj words current-word)
            (;; Beginning of a word
             [nil :U]
             [nil :L]
             ;; OR An upper-case followed by an upper case. They belong to the
             ;; same word, e.g. [get] [CP] U [Name] -> [get] [CPU] [Name]
             [:U :U]
             ;; OR a lower-case followed by a lowercase. They're part of the
             ;; same word. [ge] t [CPUCount] -> [get] C [PUCount]
             [:L :L])
            ;; -> This character belongs to the current word. Process the
            ;; next caracter
            (recur words (conj current-word current-char) rest-of-remaining-chars current-char next-char)
            ;; an lower-case followed by an upper-case. This marks the end of
            ;; the current word,  e.g. [get] C [PUName] -> [get] [C] P [UName]
            [:L :U]
            ;; -> store the current word, as it is finished. Start with a
            ;; new word with the upper-case character
            (recur (conj words current-word) [current-char] rest-of-remaining-chars nil next-char)
            ;; An upper-case followed by a lower-case. Both are part of a new
            ;; word, e.g. [get] [CPUN] a [me] -> [get] [CPU] [Na] m [e]
            [:U :L]
            ;; -> the last and current chars actually belong to a new
            ;; word. That means that the current word has an extra
            ;; char at the end
            (recur (vec (when (butlast current-word) (conj words (vec (butlast current-word)))))
                   [last-char current-char] rest-of-remaining-chars current-char next-char)))))

(defn camel-to-clojure-style
  "Converts a camelCasedJAVAWord into a clojure-style-all-smallcaps-with-dashes-word.

NOTE: Currently only works well when the name contains nonumbers or any valid separators
 (e.g. \"_\")"
  [name]
  (let [first-char (first name)
        rest-of-name (rest name)
        character-groups (camel-to-clojure-style* [] [] rest-of-name nil first-char)
        lowercased-words (map #(.toLowerCase (apply str %)) character-groups)]
    (apply str (interpose "-" lowercased-words))))


(defn methods-starting-with
  "Obtain a list of a methods whose name starts with 'starting'"
  [^Class class starting]
  (letfn [(valid-method?
           [^java.lang.reflect.Method method]
           (and (= java.lang.reflect.Modifier/PUBLIC (.getModifiers method))
                (.startsWith (.getName method) starting)))]
    (let [methods (filter valid-method? (.getDeclaredMethods class))
          name (fn [method] (.getName method))]
      ;; create a map {method-name, method}
      (into {} (for [method methods] [(name method) method])))))

(defn remove-first-word-from-clojure-name [name]
  "removes the firts word from the beginning of a clojure-style name
e.g. get-cpu-name -> cpu-name"
  (let [words (rest (split name #"-"))]
    (apply str (interpose "-" words))))

(def attribute-name-from-accessor
  (comp remove-first-word-from-clojure-name
        camel-to-clojure-style))

(defrecord method-signature
  [method-name
   return-type
   parameter-types])

(defn method-call-fn
  ([method-name return-type parameter-types]
     (let [method-symbol (read-string method-name)
           args (map #(with-meta (symbol (str "x" %2)) {:tag %1}) parameter-types (range))]
       ;;(println "producing: " `(fn [o# ~@args] (. o# ~method-symbol ~@args )))
       (eval `(fn [o# ~@args] (. o# ~method-symbol ~@args )))))
  ([^method-signature signature]
     (let [{:keys [method-name return-type parameter-types]} signature]
       (method-call-fn method-name return-type parameter-types))))

(defn get-signature [^java.lang.reflect.Method method]
  (let [method-name (.getName method)
        return-type (.getReturnType method)
        parameter-types (vec (.getParameterTypes method))]
    {:method-name method-name
     :return-type return-type
     :parameter-types parameter-types}))

(comment
 (def eval-method-call (method-call-fn "equals" boolean [java.lang.Object]))
 (eval-method-call "hello" "hello")
 ;; true
 (eval-method-call "hello" "goodbye")
 ;; false
 )


(defn gettable-attribute-method-map
  [class]
  (let [getters-map (methods-starting-with class "get")]
    (into {} (for [[name method] getters-map]
               [((comp keyword attribute-name-from-accessor) name)
                ((comp method-call-fn get-signature) method)]))))

(defn settable-attribute-method-map
  [class]
  (let [getters-map (methods-starting-with class "set")]
    (into {} (for [[name method] getters-map]
               [((comp keyword attribute-name-from-accessor) name)
                ((comp method-call-fn get-signature) method)]))))

