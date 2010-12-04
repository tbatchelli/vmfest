(ns vmfest.virtualbox.enums
  (:require [clojure.contrib.logging :as log])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IMachine MachineState ClipboardMode PointingHidType
            FirmwareType KeyboardHidType SessionState]))


(defmacro find-key-by-value [value table]
  `(let [[v# k# _#] (first (filter (fn [[v# _# _#]] (= ~value v#)) ~table))]
    (when-not k# (log/warn (str "Key not found for value=" ~value " in " '~table)))
    k#))

(defmacro find-value-by-key [key table]
  `(let [[v# k# _#] (first (filter (fn [[_# k# _#]] (= ~key k#)) ~table))]
    (when-not v# (log/warn (str "Value not found for key=" ~key " in " '~table)))
    v#))


;;; MachineState
(def machine-state-to-key-table
  [[MachineState/PoweredOff :powered-off ""]
   [MachineState/Saved :saved ""]
   [MachineState/Teleported :teleported ""]
   [MachineState/Aborted :aborted ""]
   [MachineState/Running :running ""]
   [MachineState/Paused :paused ""]
   [MachineState/Stuck :stuck ""]
   [MachineState/Teleporting :teleporting ""]
   [MachineState/LiveSnapshotting :live-snapshotting ""]
   [MachineState/Starting :starting ""]
   [MachineState/Stopping :stopping ""]
   [MachineState/Saving :saving ""]
   [MachineState/Restoring :restoring ""]
   [MachineState/TeleportingPausedVM :teleporting-paused-vm ""]
   [MachineState/TeleportingIn :teleporting-in ""]
   [MachineState/DeletingSnapshotOnline :deleting-snapshot-online ""]
   [MachineState/DeletingSnapshotPaused :deleting-snapshot-paused ""]
   [MachineState/RestoringSnapshot :restoring-snapshot ""]
   [MachineState/DeletingSnapshot :deleting-snapshot ""]
   [MachineState/SettingUp :setting-up ""]
   [MachineState/FirstOnline :first-online ""]
   [MachineState/LastOnline :last-online ""]
   [MachineState/FirstTransient :first-transient ""]
   [MachineState/LastTransient :last-transient ""]])

(defn machine-state-to-key [state]
  (find-key-by-value state machine-state-to-key-table))

(defn key-to-machine-state [key]
  (find-value-by-key key machine-state-to-key-table))

;;; ClipboardMode
(def clipboard-mode-to-key-table
  [[ClipboardMode/Disabled :disabled ""]
   [ClipboardMode/HostToGuest :host-to-guest ""]
   [ClipboardMode/GuestToHost :guest-to-host ""]
   [ClipboardMode/Bidirectional :bidirectional ""]])

(defn clipboard-mode-to-key [mode]
  (find-key-by-value mode clipboard-mode-to-key-table))

(defn key-to-clipboard-mode [key]
  (find-value-by-key key clipboard-mode-to-key-table))

;;; PointingHidType
(def pointing-hid-type-to-key-table
  [[PointingHidType/None :none ""]
   [PointingHidType/PS2Mouse :ps2-mouse ""]
   [PointingHidType/USBMouse :usb-mouse ""]
   [PointingHidType/USBTablet :usb-tablet ""]
   [PointingHidType/ComboMouse :combo-mouse ""]])

(defn pointing-hid-type-to-key [type]
  (find-key-by-value type pointing-hid-type-to-key-table))

(defn key-to-pointing-hid-type [key]
  (find-value-by-key key pointing-hid-type-to-key-table))

;;; FirmwareType
(def firmware-type-to-key-table
  [[FirmwareType/BIOS :bios ""]
   [FirmwareType/EFI :efi ""]
   [FirmwareType/EFI32 :efi-32 ""]
   [FirmwareType/EFI64 :efi-64 ""]
   [FirmwareType/EFIDUAL :efi-dual ""]])

(defn firmware-type-to-key [type]
  (find-key-by-value type firmware-type-to-key-table))

(defn key-to-firmware-type [key]
  (find-value-by-key key firmware-type-to-key-table))
;;; KeyboardHidType
(def keyboard-hid-type-to-key-table
  [[KeyboardHidType/None :none ""]
   [KeyboardHidType/PS2Keyboard :ps2-keyboard ""]
   [KeyboardHidType/USBKeyboard :usb-keyboard ""]
   [KeyboardHidType/ComboKeyboard :combo-keyboard ""]])
(defn keyboard-hid-type-to-key [type]
  (find-key-by-value type keyboard-hid-type-to-key-table))
(defn key-to-keyboard-hid-type [key]
  (find-value-by-key key keyboard-hid-type-to-key-table))


;;; SessionState
(def session-state-to-key-table
  [[SessionState/Null :null ""]
   [SessionState/Closed :closed ""]
   [SessionState/Open :open ""]
   [SessionState/Spawning :spawning ""]
   [SessionState/Closing :closing ""]])
(defn session-state-to-key [state]
  (find-key-by-value state session-state-to-key-table))
(defn key-to-session-state [key]
  (find-value-by-key key session-state-to-key-table))
