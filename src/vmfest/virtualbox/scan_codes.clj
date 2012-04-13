(ns vmfest.virtualbox.scan-codes
  (:use [slingshot.slingshot :only (throw+)]))

(defn keyp
  "Keypress and release key scan codes"
  [h]
  [h (bit-or h 0x80)])

(defn shift [h]
  `[0x2a ~@(keyp h) 0xaa])

(def sc-char-map
  {0x02 "1!"
   0x03 "2@"
   0x04 "3#"
   0x05 "4$"
   0x06 "5%"
   0x07 "6^"
   0x08 "7&"
   0x09 "8*"
   0x0a "9("
   0x0b "0)"
   0x0c "-_"
   0x0d "=+"
   0x10 "qQ"
   0x11 "wW"
   0x12 "eE"
   0x13 "rR"
   0x14 "tT"
   0x15 "yY"
   0x16 "uU"
   0x17 "iI"
   0x18 "oO"
   0x19 "pP"
   0x1a "[{"
   0x1b "]}"
   0x1e "aA"
   0x1f "sS"
   0x20 "dD"
   0x21 "fF"
   0x22 "gG"
   0x23 "hH"
   0x24 "jJ"
   0x25 "kK"
   0x26 "lL"
   0x27 ";:"
   0x28 "'\""
   0x29 "`~"
   0x2b "\\|" ; on a 102-key keyboard
   0x2c "zZ"
   0x2d "xX"
   0x2e "cC"
   0x2f "vV"
   0x30 "bB"
   0x31 "nN"
   0x32 "mM"
   0x33 ",<"
   0x34 ".>"
   0x35 "/?"})

(def sc-non-char-map
  {
   0x01 :esc
   0x39 \space
   0x0e :backspace
   0x0f :tab,
   0x1c :enter
   0x1d :l-ctrl
   0x2a :l-shift
   0x36 :r-shift
   0x37 :keypad-* ;or (*/PrtScn) on a 83/84-key keyboard
   0x38 :l-alt
   0x3a :caps-lock
   0x3b :f1
   0x3c :f2
   0x3d :f3
   0x3e :f4
   0x3f :f5
   0x40 :f6
   0x41 :f7
   0x42 :f8
   0x43 :f9
   0x44 :f10
   0x45 :num-lock
   0x46 :scroll-lock
   0x47 :keypad-7-home
   0x48 :keypad-8-up
   0x49 :keypad-9-pg-up
   0x4a :keypad--
   0x4b :keypad-4-left
   0x4c :keypad-5
   0x4d :keypad-6-right
   0x4e :keypad-+
   0x4f :keypad-1-end
   0x50 :keypad-2-down
   0x51 :keypad-3-pg-down
   0x52 :keypad-0-ins
   0x53 :keypad-.-del
   })

(def escaped
  {:keypad-enter 0x1c
   :r-ctrl 0x1d
   :fake-l-shift 0x2a
   :keypad-forward-slash 0x35
   :fake-r-shift 0x36
   :ctrl-prnscrn 0x37
   :r-alt 0x38
   :crl-break 0x46
   :home 0x47
   :up 0x48
   :pg-up 0x49
   :left 0x4b
   :right 0x4d
   :end 0x4f
   :down 0x50
   :pg-down 0x51
   :insert 0x52
   :delete 0x53})

(defn- process-sc-char-map []
  (into {}
        (reduce concat
                (for [m sc-char-map]
                  (let [[key [small cap]] m]
                    [[small (keyp key)]
                     [cap (shift key)]])))))

(defn- process-sc-non-char-map []
  (into {}
        (for [m sc-non-char-map]
          (let [[key sym] m]
            [sym (keyp key)]))))

(defn process-escaped []
  (zipmap (keys escaped)
          (map (fn [x] [0xe0 x]) (vals escaped))))

(def sc-map
  (merge
   (process-sc-char-map)
   (process-sc-non-char-map)
   (process-escaped)))

(defn non-chars []
  (filter keyword? (keys sc-map)))

(defn chars []
  (filter (complement keyword?) (keys sc-map)))

(defn scan-codes
  "Given a sequence with a mix of:
     - strings corresponding to text
     - keywords corresponding to non-char key presses
     - numbers corresponding to wait times in ms.
  returns a sequence with the corresponding scan codes to be sent from the VM's
  keyboard(in hexa) and pauses to be observed between key presses (e.g. [10]
  for 10ms).

  Note that:
   - (chars) will provide a list of permitted characters in the strings
   - (non-chars) will provide a list of the permitted commands as keywords

 Example:
  (scan-codes {:keypad-5 35 \"Abc\"})
  => (76 204 [35] 42 30 158 170 48 176 46 174)"
  [s]
  (let [translate-keyword
        (fn [e]
          (if-let [scan-codes (e sc-map)]
            scan-codes
            (throw+
             {:type :scan-code-does-not-exist
              :message
              (format
               (str "scan-codes: The key '%s' does not correspond to any"
                    " scan code. Please check"
                    " vmfest.virtualbox.scan-codes/non-chars"
                    " for a list of available codes." ) e)})))
        translate-char
        (fn [c]
          (if-let [scan-codes (get sc-map c)]
            scan-codes
            (throw+
             {:type :scan-code-does-not-exist
              :message
              (format
               (str "scan-codes: The character '%s' does not correspond to any"
                    " scan code. Please check"
                    " vmfest.virtualbox.scan-codes/chars"
                    " for a list of available codes." ) c)})))
        translate-num (fn [n] [[n]])]
    (mapcat
     (fn [e]
       (if (keyword? e)
         (translate-keyword e)
         (if (number? e)
           (translate-num e)
           (mapcat translate-char e))))
     s)))
