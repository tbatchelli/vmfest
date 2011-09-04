(ns vmfest.virtualbox.enums
  (:require [clojure.tools.logging :as log])
  (:import
   [org.virtualbox_4_0 IMachine MachineState ClipboardMode PointingHidType
    FirmwareType KeyboardHidType SessionState SessionType StorageBus
    DeviceType NetworkAttachmentType CleanupMode StorageControllerType
    MediumType HostNetworkInterfaceType]))


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
   [SessionState/Unlocked :unlocked ""]
   [SessionState/Locked :locked ""]
   [SessionState/Spawning :spawning ""]
   [SessionState/Unlocking :unlocking ""]])
(defn session-state-to-key [state]
  (find-key-by-value state session-state-to-key-table))
(defn key-to-session-state [key]
  (find-value-by-key key session-state-to-key-table))

;;; SessionType
(def session-type-to-key-table
  [[SessionType/Null :null ""]
   [SessionType/WriteLock :write-lock ""]
   [SessionType/Remote :remote ""]
   [SessionType/Shared :shared ""]])
(defn session-type-to-key [type]
  (find-key-by-value type session-type-to-key-table))
(defn key-to-session-type [key]
  (find-value-by-key key session-type-to-key-table))

;;;StorageBus
(def storage-bus-to-key-table
  [[StorageBus/Null :null ""]
   [StorageBus/IDE :ide ""]
   [StorageBus/SATA :sata ""]
   [StorageBus/SCSI :scsi ""]
   [StorageBus/Floppy :floppy ""]
   [StorageBus/SAS :sas ""]])
(defn storage-bus-to-key [bus]
  (find-key-by-value bus storage-bus-to-key-table))
(defn key-to-storage-bus [key]
  (find-value-by-key key storage-bus-to-key-table))

(def storage-controller-type-to-key-table
  [[StorageControllerType/LsiLogic :lsi-logic ""]
   [StorageControllerType/BusLogic :bus-logic ""]
   [StorageControllerType/IntelAhci :intel-ahci ""]
   [StorageControllerType/Null :null ""]
   [StorageControllerType/PIIX3 :piix3 ""]
   [StorageControllerType/PIIX4 :piix4 ""]
   [StorageControllerType/ICH6 :ich6 ""]
   [StorageControllerType/I82078 :i82087 ""]
   [StorageControllerType/LsiLogicSas :lsi-logic-sas ""]])
(defn storage-controller-type-to-key [type]
  (find-key-by-value type storage-controller-type-to-key-table))
(defn key-to-storage-controller-type [key]
  (find-value-by-key key storage-controller-type-to-key-table))

;;; DeviceType
(def device-type-to-key-table
  [[DeviceType/Null
    :null "Null value, may also mean “no device” (not allowed for IConsole::getDeviceActivity())."]
   [DeviceType/Floppy :floppy "Floppy device."]
   [DeviceType/DVD :dvd "CD/DVD-ROM device."]
   [DeviceType/HardDisk :hard-disk "Hard disk device."]
   [DeviceType/Network :network "Network device."]
   [DeviceType/USB :usb "USB device."]
   [DeviceType/SharedFolder :shared-folder "Shared folder device."]])
(defn device-type-to-key [type]
  (find-key-by-value type device-type-to-key-table))
(defn key-to-device-type [key]
  (find-value-by-key key device-type-to-key-table))

;;; NetworkAttachmentType
(def network-attachment-type-to-key-table
  [[NetworkAttachmentType/Null :null "Null value, also means 'not attached'."]
   [NetworkAttachmentType/NAT :nat ""]
   [NetworkAttachmentType/Bridged :bridged ""]
   [NetworkAttachmentType/Internal :internal ""]
   [NetworkAttachmentType/HostOnly :host-only ""]
   [NetworkAttachmentType/VDE :vde ""]])
(defn network-attachment-type-to-key [type]
  (find-key-by-value type network-attachment-type-to-key-table))
(defn key-to-network-attachment-type [key]
  (find-value-by-key key network-attachment-type-to-key-table))

;;; MediumType
(def medium-type-type-to-key-table
  [[MediumType/Immutable :immutable
    "Normal medium (attached directly or indirectly, preserved when taking snapshots)."]
   [MediumType/Normal :normal
    "Normal medium (attached directly or indirectly, preserved when taking snapshots)."]
   [MediumType/Writethrough :write-through
    "Write through medium (attached directly, ignored when taking snapshots)."]
   [MediumType/Shareable :shareable
    "Allow using this medium concurrently by several machines."]
   [MediumType/Readonly :readonly
    "A readonly medium, which can of course be used by several machines." ]
   [MediumType/MultiAttach :multi-attach
    "A medium which is is indirectly attached, so that one base medium can be used for several VMs which have their own differencing medium to store their modifications. In some sense a variant of Immutable with unset AutoReset flag in each differencing medium."]])
(defn medium-type-type-to-key [type]
  (find-key-by-value type medium-type-type-to-key-table))
(defn key-to-medium-type-type [key]
  (find-value-by-key key medium-type-type-to-key-table))

(def host-network-interface-type-to-key-table
  [[HostNetworkInterfaceType/Bridged :bridged ""]
   [HostNetworkInterfaceType/HostOnly :host-only ""]])
(defn host-network-interface-type-to-key [type]
  (find-key-by-value type host-network-interface-type-to-key-table))
(defn key-to-host-network-interface-type [key]
  (find-value-by-key key host-network-interface-type-to-key-table))


;;; CleanupMode
(def cleanup-mode-type-to-key-table
  [[CleanupMode/UnregisterOnly :unregister-only
    (str "Unregister only the machine, but"
         " neither delete snapshots nor detach media.")]
   [CleanupMode/DetachAllReturnNone :detach-all-return-none
    (str "Delete all snapshots and detach all media but return none;"
         " this will keep all media registered.")]
   [CleanupMode/DetachAllReturnHardDisksOnly :detach-all-return-hard-disks-only
    (str "Delete all snapshots, detach all media and return hard disks for"
         " closing, but not removeable media.")]
   [CleanupMode/Full :full
    (str "Delete all snapshots, detach all media and return all media"
         " for closing.")]])
(defn cleanup-mode-type-to-key [type]
  (find-key-by-value type cleanup-mode-type-to-key-table))
(defn key-to-cleanup-mode [key]
  (find-value-by-key key cleanup-mode-type-to-key-table))
