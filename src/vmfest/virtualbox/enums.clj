(ns vmfest.virtualbox.enums
  (:require [clojure.contrib.logging :as log])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IMachine]
           [org.virtualbox_3_2  MachineState ClipboardMode PointingHidType
            FirmwareType KeyboardHidType SessionState SessionType]))

(defmacro find-key-by-value [value table]
  `(let [[v# k# _#] (first (filter (fn [[v# _# _#]] (= ~value v#)) ~table))]
    (when-not k# (log/warn (str "Key not found for value=" ~value " in " '~table)))
    k#))

#_(defn find-key-by-value [value table]
  (let [[v k _] (first (filter (fn [[v _ _]]
                                 (= v value)) table))]
    (when-not k (log/warn (str "Key not found for value=" value " in " 'table)))
    k))

(defmacro find-value-by-key [key table]
  `(let [[v# k# _#] (first (filter (fn [[_# k# _#]] (= ~key k#)) ~table))]
    (when-not v# (log/warn (str "Value not found for key=" ~key " in " '~table)))
    v#))

;;; MachineState
(def machine-state-to-key-table
  [[MachineState/POWERED_OFF :powered-off ""]
   [MachineState/SAVED :saved ""]
   [MachineState/TELEPORTED :teleported ""]
   [MachineState/ABORTED :aborted ""]
   [MachineState/RUNNING :running ""]
   [MachineState/PAUSED :paused ""]
   [MachineState/STUCK :stuck ""]
   [MachineState/TELEPORTING :teleporting ""]
   [MachineState/LIVE_SNAPSHOTTING :live-snapshotting ""]
   [MachineState/STARTING :starting ""]
   [MachineState/STOPPING :stopping ""]
   [MachineState/SAVING :saving ""]
   [MachineState/RESTORING :restoring ""]
   [MachineState/TELEPORTING_PAUSED_VM :teleporting-paused-vm ""]
   [MachineState/TELEPORTING_IN :teleporting-in ""]
   [MachineState/DELETING_SNAPSHOT_ONLINE :deleting-snapshot-online ""]
   [MachineState/DELETING_SNAPSHOT_PAUSED :deleting-snapshot-paused ""]
   [MachineState/RESTORING_SNAPSHOT :restoring-snapshot ""]
   [MachineState/DELETING_SNAPSHOT :deleting-snapshot ""]
   [MachineState/SETTING_UP :setting-up ""]
   [MachineState/FIRST_ONLINE :first-online ""]
   [MachineState/LAST_ONLINE :last-online ""]
   [MachineState/FIRST_TRANSIENT :first-transient ""]
   [MachineState/LAST_TRANSIENT :last-transient ""]])

(defn machine-state-to-key [state]
  (find-key-by-value state machine-state-to-key-table))

(defn key-to-machine-state [key]
  (find-value-by-key key machine-state-to-key-table))

;;; ClipboardMode
(def clipboard-mode-to-key-table
  [[ClipboardMode/DISABLED :disabled ""]
   [ClipboardMode/HOST_TO_GUEST :host-to-guest ""]
   [ClipboardMode/GUEST_TO_HOST :guest-to-host ""]
   [ClipboardMode/BIDIRECTIONAL :bidirectional ""]])

(defn clipboard-mode-to-key [mode]
  (find-key-by-value mode clipboard-mode-to-key-table))

(defn key-to-clipboard-mode [key]
  (find-value-by-key key clipboard-mode-to-key-table))

;;; PointingHidType
(def pointing-hid-type-to-key-table
  [[PointingHidType/NONE :none ""]
   [PointingHidType/PS_2_MOUSE :ps2-mouse ""]
   [PointingHidType/USB_MOUSE :usb-mouse ""]
   [PointingHidType/USB_TABLET :usb-tablet ""]
   [PointingHidType/COMBO_MOUSE :combo-mouse ""]])

(defn pointing-hid-type-to-key [type]
  (find-key-by-value type pointing-hid-type-to-key-table))

(defn key-to-pointing-hid-type [key]
  (find-value-by-key key pointing-hid-type-to-key-table))

;;; FirmwareType
(def firmware-type-to-key-table
  [[FirmwareType/BIOS :bios ""]
   [FirmwareType/EFI :efi ""]
   [FirmwareType/EFI_32 :efi-32 ""]
   [FirmwareType/EFI_64 :efi-64 ""]
   [FirmwareType/EFIDUAL :efi-dual ""]])

(defn firmware-type-to-key [type]
  (find-key-by-value type firmware-type-to-key-table))

(defn key-to-firmware-type [key]
  (find-value-by-key key firmware-type-to-key-table))
;;; KeyboardHidType
(def keyboard-hid-type-to-key-table
  [[KeyboardHidType/NONE :none ""]
   [KeyboardHidType/PS_2_KEYBOARD :ps2-keyboard ""]
   [KeyboardHidType/USB_KEYBOARD :usb-keyboard ""]
   [KeyboardHidType/COMBO_KEYBOARD :combo-keyboard ""]])
(defn keyboard-hid-type-to-key [type]
  (find-key-by-value type keyboard-hid-type-to-key-table))
(defn key-to-keyboard-hid-type [key]
  (find-value-by-key key keyboard-hid-type-to-key-table))

;;; SessionState
(def session-state-to-key-table
  [[SessionState/NULL :null ""]
   [SessionState/CLOSED :closed ""]
   [SessionState/OPEN :open ""]
   [SessionState/SPAWNING :spawning ""]
   [SessionState/CLOSING :closing ""]])
(defn session-state-to-key [state]
  (find-key-by-value state session-state-to-key-table))
(defn key-to-session-state [key]
  (find-value-by-key key session-state-to-key-table))

(def session-type-to-key-table
  [[SessionType/NULL :null ""]
   [SessionType/DIRECT :direct ""]
   [SessionType/REMOTE :remote ""]
   [SessionType/EXISTING :existing ""]])
(defn session-type-to-key [type]
  (find-key-by-value type session-type-to-key-table))
(defn key-to-session-type [key]
  (find-value-by-key key session-type-to-key-table))
