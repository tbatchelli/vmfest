(ns vmfest.util
  (:import java.lang.Character))

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